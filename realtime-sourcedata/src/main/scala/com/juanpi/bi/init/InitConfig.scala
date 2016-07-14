package com.juanpi.bi.init

import java.util.Properties
import java.io.FileInputStream

import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Table}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.storage.StorageLevel

/**
  * 默认情况下 Scala 使用不可变 Map。如果你需要使用可变集合，你需要显式的引入 import scala.collection.mutable.Map 类
  * 参见 ：https://wizardforcel.gitbooks.io/w3school-scala/content/17.html
   */
import scala.collection.mutable

/**
  * Created by gongzi on 2016/7/8.
  */
class InitConfig {
  var hbaseZk = ""
  var hbasePort = ""
  var zkQuorum = ""
  var hbase_family = ""

  val dimPages = new mutable.HashMap[String, (Int, Int, String, Int)]
  var dimEvents = new mutable.HashMap[String, Int]
  var ticks_history: Table = null
  val table_ticks_history = TableName.valueOf("ticks_history")

  var conf = new SparkConf().set("spark.akka.frameSize", "256")
    .set("spark.kryoserializer.buffer.max", "512m")
    .set("spark.kryoserializer.buffer", "256m")
    .set("spark.scheduler.mode", "FAIR")
    .set("spark.storage.blockManagerSlaveTimeoutMs", "8000000")
    .set("spark.storage.blockManagerHeartBeatMs", "8000000")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.rdd.compress", "true")
    .set("spark.io.compression.codec", "org.apache.spark.io.SnappyCompressionCodec")
    // control default partition number
    .set("spark.streaming.blockInterval", "10000")
    .set("spark.shuffle.manager", "SORT")
    .set("spark.eventLog.overwrite", "true")

  this.loadProperties()

  def init() = {

    // 查询 hive 中的 dim_page 和 dim_event
    val sc: SparkContext = new SparkContext(conf)
    val sqlContext: HiveContext = new HiveContext(sc)

    initDimPage(sqlContext)
    initDimPage(sqlContext)

  }

  def loadProperties():Unit = {
    val properties = new Properties()
    val path = Thread.currentThread().getContextClassLoader.getResource("conf.properties").getPath //文件要放到resource文件夹下
    properties.load(new FileInputStream(path))
    hbasePort = properties.getProperty("hbase.zookeeper.property.clientPort")
    zkQuorum = properties.getProperty("zkQuorum")
    hbaseZk = properties.getProperty("hbaseZk")
    hbase_family = properties.getProperty("hbase_family")
  }

  def getHbaseConf(): Connection = {
    val hbaseConf = HBaseConfiguration.create()
    hbaseConf.set("hbase.zookeeper.property.clientPort", hbasePort)
    hbaseConf.set("hbase.zookeeper.quorum", zkQuorum)
    hbaseConf.setInt("timeout", 120000)
    //Connection 的创建是个重量级的工作，线程安全，是操作hbase的入口
    ConnectionFactory.createConnection(hbaseConf)
  }

  def initDimPage(sqlContext: HiveContext) =
  {
    val dimPageSql = s"""select page_id,page_exp1, page_exp2, page_type_id, page_value, page_level_id, concat_ws(",", url1, url2, url3,regexp1, regexp2, regexp3) as url_pattern
                         | from dw.dim_page
                         | where page_id > 0
                         | and terminal_lvl1_id = 2
                         | and del_flag = 0
                         | order by page_id'""".stripMargin

    val dimPageData = sqlContext.sql(dimPageSql).persist(StorageLevel.MEMORY_AND_DISK)

    dimPageData.foreach(line => {
      val page_id: Int = line.getAs[Int]("page_id")
      val page_exp1 = line.getAs[String]("page_exp1")
      val page_exp2 = line.getAs[String]("page_exp2")
      val page_value = line.getAs[String]("page_value")
      val page_type_id = line.getAs[Int]("page_type_id")
      val page_level_id = line.getAs[Int]("page_level_id")
      // 移动端的 page_exp1+page_exp2 不会为空，但是 url_pattern 为空
//      val url_pattern = line.getAs[String]("url_pattern")
      dimPages += (page_exp1+page_exp2 -> (page_id, page_type_id, page_value, page_level_id))
//      dimPages += ((page_exp1+page_exp2) -> (page_id, page_type_id, page_value, page_level_id))
//      val scores3 = new scala.collection.mutable.HashMap[String,Int]
//      scores3 += (page_exp1 -> page_id)

    })
    dimPageData.unpersist(true)
  }

  def initDimEvent(sqlContext: HiveContext) =
  {
    val dimEventSql = s"""select event_id, event_exp1, event_exp2
                         | from dw.dim_event
                         | where event_id > 0
                         | and terminal_lvl1_id = 2
                         | and del_flag = 0
                         | order by event_id'""".stripMargin

    val dimData = sqlContext.sql(dimEventSql).persist(StorageLevel.MEMORY_AND_DISK)

    dimData.foreach(line => {
      val event_id = line.getAs[Int]("event_id")
      val event_exp1 = line.getAs[String]("event_exp1")
      val event_exp2 = line.getAs[String]("event_exp2")
      dimEvents += (event_exp1+event_exp2 -> event_id)
    })

    dimData.unpersist(true)
  }
}

object InitConfig {

  val ic = new InitConfig()
  val HbaseFamily = ic.hbase_family
  val MySparkConf = ic.conf

  ic.init()

  val dimPages = ic.dimPages
  val dimEvents = ic.dimEvents

  // hbase 创建连接
  def initTicksHistory(): Table =
  {
    ic.getHbaseConf().getTable(ic.table_ticks_history)
  }

  def main(args: Array[String]) {
    val ic = new InitConfig()
//    ic.loadProperties
//    println(ic.brokerList)
//
//    println(ic.brokerList, ic.consumerTime, ic.groupId, ic.hbaseZk, ic.zkQuorum)
  }
}
