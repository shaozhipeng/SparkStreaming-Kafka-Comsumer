//package com.juanpi.bi.streaming
//
//import java.io.Serializable
//
//import com.juanpi.bi.bean.{Event, Page, PageAndEvent, User}
//import com.juanpi.bi.commons.kafka.{ConsumerConfig, KafkaClient}
//import com.juanpi.bi.init.InitConfig
//import com.juanpi.bi.transformer.{ITransformer, pageAndEventParser}
//import org.apache.hadoop.io.NullWritable
//import org.apache.hadoop.mapred.lib.MultipleTextOutputFormat
//import org.apache.spark.Logging
//import org.apache.spark.rdd.RDD
//import org.apache.spark.storage.StorageLevel
//import org.apache.spark.streaming.dstream.DStream
//import org.apache.spark.streaming.kafka.KafkaManager
//import org.apache.spark.streaming.{StreamingContext, Time}
//
//import scala.collection.mutable
//
//class KafkaConsumerNew(topic: String, dimPage: mutable.HashMap[String, (Int, Int, String, Int)],
//                       dimEvent: mutable.HashMap[String, (Int, Int)])
//  extends Logging with Serializable {
//
//  import KafkaConsumer._
//
//  var logTransformer: ITransformer = null
//
//  /**
//    * 解析 event
//    * event 过滤 collect_api_responsetime
//    * page 和 event 都需要过滤 gu_id 为空的数据，需要过滤 site_id 不为（2, 3）的数据
//    *
//    * @param dataDStream
//    * @param ssc
//    * @param km
//    */
//  def eventProcess(dataDStream: DStream[((Long, Long), String)],
//                   ssc: StreamingContext, km: KafkaManager) = {
//    // event 中直接顾虑掉 activityname = "collect_api_responsetime" 的数据
//    // 数据块中的每一条记录需要处理
//    val sourceLog = dataDStream.persist(StorageLevel.MEMORY_AND_DISK_SER)
//    val data = sourceLog.map(_._2.replace("\0", ""))
//      .filter(line => !line.contains("collect_api_responsetime"))
//      .map(msg => parseMessage(msg))
//      .filter(_._1.nonEmpty)
//
//    data.foreachRDD((rdd, time) => {
//      // 保存数据至hdfs
//      rdd.map(v => ((v._1, time.milliseconds), v._3))
//        .repartition(1)
//        .saveAsHadoopFile(Config.baseDir + "/" + topic,
//          classOf[String],
//          classOf[String],
//          classOf[RDDMultipleTextOutputFormat])
//    })
//
//    // 更新kafka offset
//    sourceLog.foreachRDD { rdd =>
//      km.updateOffsets(rdd)
//    }
//  }
//
//  /**
//    * 解析 pageinfo
//    * @param dataDStream
//    * @param ssc
//    * @param km
//    */
//  def pageProcess(dataDStream: DStream[((Long, Long), String)],
//                  ssc: StreamingContext,
//                  km: KafkaManager) = {
//    // event 中直接顾虑掉 activityname = "collect_api_responsetime" 的数据
//    // 数据块中的每一条记录需要处理
//    //    val sourceLog = dataDStream.persist(StorageLevel.MEMORY_AND_DISK_SER)
//
//    val data = dataDStream.map(_._2.replace("\0", ""))
//      .map(msg => parseMessage(msg))
//      .filter(_._1.nonEmpty)
//
//    data.foreachRDD((rdd, time) => {
//
//      //  需要从 hbase 查 utm 和 gu_id 的值，存在就取出来，否则写 hbase
//      val newRdd = rdd.map(record => {
//        val (user: User, pageAndEvent: PageAndEvent, page: Page, event: Event) = record._3
//
//        //          val gu_id = user.gu_id
//        //
//        //          val app_name = user.site_id match {
//        //            case 1 => "jiu"
//        //            case 2 => "zhe"
//        //            case _ => ""
//        //          }
//
//        //          val (utm, gu_create_time) = HBaseHandler.getGuIdUtmInitDate(zkQuorum, gu_id + app_name)
//        //          user.utm = utm
//        //          user.gu_create_time = gu_create_time
//        // record._2 就是 page
//        // date=2016-08-26/gu_hash=f
//        val res_str = pageAndEventParser.combineTuple(user, pageAndEvent, page, event).map(x => x match {
//          case z if z == null => "\\N"
//          case y if y.toString.isEmpty => "\\N"
//          case _ => x
//        }).mkString("\001")
//        ((record._1, time.milliseconds), res_str)
//      })
//      //
//
//      // 保存数据至hdfs: /user/hadoop/gongzi/dw_real_for_path_list/mb_pageinfo_hash2/
//      // /user/hadoop/gongzi/dw_real_for_path_list/mb_pageinfo_hash2/date=2016-08-28/gu_hash=0
//      newRdd.repartition(1)
//        .saveAsHadoopFile(Config.baseDir + "/" + topic,
//          classOf[String],
//          classOf[String],
//          classOf[RDDMultipleTextOutputFormat])
//    })
//
//    // 更新kafka offset
//    dataDStream.foreachRDD { rdd =>
//      km.updateOffsets(rdd)
//    }
//  }
//
//  //
//  // 添加时间戳
//  //
//  def addTime[C, T](rdd: RDD[(C, T)], time: Time) = {
//    val timestamp = time.milliseconds
//    rdd.map(t => ((t._1, timestamp), t._2))
//  }
//
//  def parseMessage(message: String): (String, String, Any) = {
//    getTransformer().logParser(message, dimPage, dimEvent)
//  }
//
//  def transMessage(rdd: RDD[String]): RDD[(String, String, Any)] = {
//    rdd.map { msg => parseMessage(msg) }
//  }
//
//  def getTransformer(): ITransformer = {
//    if (logTransformer == null) {
//      logTransformer = Class.forName(Config.getTopicTransformerClass(topic)).newInstance().asInstanceOf[ITransformer]
//    }
//    logTransformer
//  }
//
//}
//
//object KafkaConsumerNew {
//
//  class RDDMultipleTextOutputFormat extends MultipleTextOutputFormat[Any, Any] {
//    override def generateActualKey(key: Any, value: Any): Any = {
//      NullWritable.get()
//    }
//
//    override def generateFileNameForKeyValue(key: Any, value: Any, name: String): String = {
//      // date=2016-08-28/gu_hash=0
//      val keyAndTime = key.asInstanceOf[(String, Long)]
//      val realKey = keyAndTime._1
//      val timestamp = keyAndTime._2
//      realKey + "/logs/part_" + timestamp
//    }
//  }
//
//  /**
//    * "zkQuorum":"GZ-JSQ-JP-BI-KAFKA-001.jp:2181,GZ-JSQ-JP-BI-KAFKA-002.jp:2181,GZ-JSQ-JP-BI-KAFKA-003.jp:2181,GZ-JSQ-JP-BI-KAFKA-004.jp:2181,GZ-JSQ-JP-BI-KAFKA-005.jp:2181" "brokerList":"kafka-broker-000.jp:9082,kafka-broker-001.jp:9083,kafka-broker-002.jp:9084,kafka-broker-003.jp:9085,kafka-broker-004.jp:9086,kafka-broker-005.jp:9087,kafka-broker-006.jp:9092,kafka-broker-007.jp:9093,kafka-broker-008.jp:9094,kafka-broker-009.jp:9095,kafka-broker-010.jp:9096,kafka-broker-011.jp:9097" "topic":"mb_pageinfo_hash2" "groupId":"pageinfo_direct_dw" "consumerType":1 "consumerTime":5
//    *
//    * @param args
//    */
//  def main(args: Array[String]) {
//
//    println("======>> com.juanpi.bi.streaming.KafkaConsumer 开始运行，参数个数：" + args.length)
//
//    if (args.length < 3) {
//      System.err.println(
//        s"""
//           |Usage: KafkaConsumerOffset <zkQuorum> <brokers> <topic> <groupId> <consumerType> <consumerTime>
//           | 192.168.16.50:8081 192.168.16.50:8081 pageinfo pageinfo_direct_dw 1 60
//           |  <zkQuorum> zookeeper address to save kafka consumer offsets
//           |  <brokers> is a list of one or more Kafka brokers
//           |  <topic> topic name
//           |  <table> table name
//           |  <groupId> consumer groupId name
//           |  <consumerType> consumer type 1-every batch offset save 2-recove from 5 minutes save datetime
//           |  <consumerTime> recoved time
//           |  <maxRecords> max number of records per second
//        """.stripMargin)
//      System.exit(1)
//    }
//
//    var (zkQuorum, brokerList, topic, groupId, consumerType, consumerTime, maxRecords) = ("", "", "", "", "1", "60", "100")
//
//    println("com.juanpi.bi.streaming.KafkaConsumer 开始运行。。。。。。传入参数如下：")
//    args.foreach(
//      arg => {
//        println(arg)
//        val k = arg.split("=")(0)
//        val v = arg.split("=")(1)
//        k match {
//          case "zkQuorum" => zkQuorum = v
//          case "brokerList" => brokerList = v
//          case "topic" => topic = v
//          case "groupId" => groupId = v
//          case "consumerType" => consumerType = v
//          case "consumerTime" => consumerTime = v
//          case "maxRecords" => maxRecords = v
//        }
//      }
//    )
//
//    println("约定的groupId是：bi_gongzi_mb_pageinfo_real_direct_by_dw 或者 bi_gongzi_mb_event_real_direct_by_dw")
//
//    if (!Config.kafkaTopicMap.contains(topic)) {
//      System.err.println(s"没有找到表:${topic}配置信息")
//      System.exit(1)
//    }
//
//    // 初始化 SparkConfig StreamingContext HiveContext
//    val ic = InitConfig
//    // 时间间隔采用的是写死的，目前是 60 s
//    ic.initParam(groupId, Config.interval, maxRecords)
//    val ssc = ic.getStreamingContext()
//    val cType = consumerType.toInt
//
//    val config = new ConsumerConfig(zkQuorum, brokerList, topic, groupId, cType, consumerTime)
//    val kafkaClient = new KafkaClient(ssc, config).getKafkaManager()
//    val message = kafkaClient._2
//    val km = kafkaClient._1
//
//    val consumer = new KafkaConsumerNew(topic, ic.DIMPAGE, ic.DIMENT)
//    // page 和 event 分开解析
//    if (topic.contains("page")) {
//      consumer.pageProcess(message, ssc, km)
//    } else if (topic.contains("event")) {
//      consumer.eventProcess(message, ssc, km)
//    } else {
//      println("请指定需要解析的kafka Topic！！")
//      System.exit(1)
//    }
//
//    ssc.start()
//    ssc.awaitTermination()
//  }
//}
//
