package com.juanpi.bi.transformer
import com.juanpi.bi.bean.{Event, Page, PageAndEvent, User}
import com.juanpi.bi.init.ScalaConstants
import com.juanpi.bi.sc_utils.DateUtils
import com.juanpi.hive.udf._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

/**
  * Created by gongzi on 2016/11/28.
  */
class H5EventTransformer {

  implicit def javaToScalaInt(d: java.lang.Integer) = d.intValue

  case class BaseLog(actName: String,
                     utmId: String,
                     goodid: String,
                     baseUrl: String,
                     baseUrlRef: String,
                     ul_id: String,
                     ul_idts: Int,
                     ul_ref: String,
                     s_uid: String,
                     timeStamp: String,
                     sessionid: String,
                     click_action_name: String,
                     click_url: String,
                     qm_device_id: String,
                     actionType: String,
                     actionName: String,
                     e_n: String,
                     eV: String,
                     ip: String,
                     qm_session_id: String,
                     qm_jpid: String)

  def logParser(line: String,
                dimPage: mutable.HashMap[String, (Int, Int, String, Int)],
                dimEvent: mutable.HashMap[String, (Int, Int)]
               ): (String, String, Any) = {

    val row = Json.parse(line)

    // web 端 gu_id 从ul_id来，H5页面的gu_id通过cookie中捕获APP的gu_id获取
    val qm_jpid = (row \ "qm_jpid").asOpt[String].getOrElse("")
    val ul_id = (row \ "ul_id").asOpt[String].getOrElse("")
    val timeStamp = (row \ "timestamp").as[String].toLong

    val gu_id = if(ul_id.isEmpty()) qm_jpid else ul_id

    val ret = if (gu_id.nonEmpty) {
      val endtime = (row \ "endtime").asOpt[String].getOrElse("")
      val server_jsonstr = (row \ "server_jsonstr").asOpt[String].getOrElse("")

      try {
        val res = parse(row, dimPage, dimEvent)
        // 过滤异常的数据，具体见解析函数 eventParser.filterOutlierPageId
        if (res == null) {
          ("", "", None)
        }
        else {
          val (user: User, pageAndEvent: PageAndEvent, page: Page, event: Event) = res
          val res_str = pageAndEventParser.combineTuple(user, pageAndEvent, page, event).map(x => x match {
            case y if y == null || y.toString.isEmpty => "\\N"
            case _ => x
          }).mkString("\001")
          val partitionStr = DateUtils.dateGuidPartitions(timeStamp, gu_id)
          (partitionStr, "h5_event", res_str)
        }
      }
      catch {
        //使用模式匹配来处理异常
        case ex: Exception => {
          println(ex.getStackTraceString)
        }
          println("=======>> h5_event: getGuid Exception!!" + "======>>异常数据:" + row)
          ("", "", None)
      }
    } else {
      println("=======>> PcEvent: getGuid Exception!!" + "======>>异常数据:" + row)
      ("", "", None)
    }
    ret
  }

  def parse(row: JsValue,
            dimPage: mutable.HashMap[String, (Int, Int, String, Int)],
            dimEvent: mutable.HashMap[String, (Int, Int)]): (User, PageAndEvent, Page, Event) = {

    // ---------------------------------------------------------------- mb_event ----------------------------------------------------------------
    val act_name = (row \ "act_name").asOpt[String].getOrElse("")
    val goodid = (row \ "goodid").asOpt[String].getOrElse("")
    val url = (row \ "url").asOpt[String].getOrElse("")
    val urlref = (row \ "urlref").asOpt[String].getOrElse("")
    val ul_id = (row \ "ul_id").asOpt[String].getOrElse("")
    val ul_idts = (row \ "ul_idts").asOpt[Int].getOrElse(0)
    val ul_ref = (row \ "ul_ref").asOpt[String].getOrElse("")
    val s_uid = (row \ "s_uid").asOpt[String].getOrElse("")
    val utmId = (row \ "utm").asOpt[String].getOrElse("")
    val timeStamp = (row \ "timestamp").asOpt[String].getOrElse("")
    val sessionid = (row \ "sessionid").asOpt[String].getOrElse("")
    val click_action_name = (row \ "click_action_name").asOpt[String].getOrElse("")
    val click_url = (row \ "click_url").asOpt[String].getOrElse("")
    val qm_device_id = (row \ "qm_device_id").asOpt[String].getOrElse("")
    val actionType = (row \ "action_type").asOpt[String].getOrElse("")
    val actionName = (row \ "action_name").asOpt[String].getOrElse("")
    val e_n = (row \ "e_n").asOpt[String].getOrElse("")
    val e_v = (row \ "e_v").asOpt[String].getOrElse("")
    val ip = (row \ "ip").asOpt[String].getOrElse("")
    val qm_session_id = (row \ "qm_session_id").asOpt[String].getOrElse("")
    val qm_jpid = (row \ "qm_jpid").asOpt[String].getOrElse("")

    val baseUrl = if ("".equals(click_url)) {
      url
    } else {
      click_url
    }

    val baseUrlRef = if ("".equals(click_url)) {
      urlref
    } else {
      url
    }

    val baseTerminalId = getTerminalIdFromBase(qm_device_id, baseUrl)

    val eventJoinKey = actionName + "-dw-" + actionType

    val actName = if ("".equals(click_action_name)) {
      actionName
    } else {
      click_action_name
    }

    val ulQt = ul_idts * 1000

    val eV = if ("goodsid".equals(e_n)) {
      new GetGoodsId().evaluate(e_v)
    } else {
      e_v
    }

    val log = BaseLog(actName, utmId, goodid, baseUrl, baseUrlRef, ul_id, ul_idts, ul_ref, s_uid, timeStamp, sessionid, click_action_name, click_url, qm_device_id, actionType, actionName, e_n, eV, ip, qm_session_id, qm_jpid)

    val res = if (qm_device_id.length > 6 && baseTerminalId == 2) {
      // m.域名且带有设备号的为APP H5页面
      parseAppH5(dimEvent, dimPage, log, eventJoinKey)
    } else {
      // 非M.域名或者设备ID长度小于7的为PC/WAP/WX
      // (length(qm_device_id) <= 6 or terminal_id <> 2)) a
      val sid = (row \ "sid").asOpt[String].getOrElse("")
      parsePcWapWx(dimEvent, dimPage, log, eventJoinKey, sid)
    }

    res
  }

  def getDwSiteId(baseUrl: String): Int = {
    if (!baseUrl.isEmpty) {
      val id: java.lang.Integer = new GetSiteId().evaluate(baseUrl)
      val siId: Int = javaToScalaInt(id)
      if (siId == ScalaConstants.siteJuanpi
        || siId == ScalaConstants.siteJiuKuaiYou
        || siId == ScalaConstants.siteAll
      ) {
        ScalaConstants.siteAll
      } else {
        ScalaConstants.siteUnknow
      }
    } else ScalaConstants.siteUnknow
  }

  def getPageLevel2Value(pageId: String, shopId: String, baseUrl: String): String = {
    val pageLevel2Value = if (pageId == "10104") {
      val skcId = new GetSkcId().evaluate(baseUrl)
      skcId
    } else if (pageId == "10102") {
      shopId
    } else if (baseUrl.contains("singlemessage")) {
      "singlemessage"
    } else if (baseUrl.contains("groupmessage")) {
      "groupmessage"
    } else if (baseUrl.contains("timeline")) {
      "timeline"
    } else {
      ""
    }
    pageLevel2Value
  }

  // 解析app端 h5 端数据
  def parseAppH5(dimEvent: mutable.HashMap[String, (Int, Int)],
                 dimPage: mutable.HashMap[String, (Int, Int, String, Int)],
                 baseLog: BaseLog,
                 eventJoinKey: String): (User, PageAndEvent, Page, Event) = {

    val baseUrlRef = baseLog.baseUrlRef
    val baseUrl = baseLog.baseUrl
    // H5页面的gu_id通过cookie中捕获APP的gu_id获取
    val qm_device_id = baseLog.qm_device_id
    val guId = if (qm_device_id.isEmpty) {
      baseLog.ul_id
    } else {
      baseLog.qm_jpid
    }

    val dwTeminalId = getTerminalIdForH5(qm_device_id)

    val appVersion = ""

    val (d_event_id: Int, event_type_id: Int) = dimEvent.get(eventJoinKey).getOrElse(0, 0)
    val eventId = d_event_id match {
      case a if a > 0 => a
      case _ => 0
    }

    val eV = baseLog.eV
    val actionName = baseLog.actionName
    val eventValue = if (event_type_id == 10) {
      val ev0 = eV.split("::")(0)
      val ev1 = eV.split("::")(1)
      val res = new GetDwPcPageValue().evaluate(ev1)
      actionName + "::" + res + "::" + ev0
    } else {
      eV
    }

    val refPageId = new GetPageID().evaluate(baseUrlRef)
    val refPageValue = new GetDwPcPageValue().evaluate(baseUrlRef)
    val refSiteId = new GetSiteId().evaluate(baseUrlRef)
    val pageId = new GetPageID().evaluate(baseUrl)
    val pageValue = new GetDwPcPageValue().evaluate(baseUrl)
    val shopId = new GetShopId().evaluate(baseUrl)
    val refShopId = new GetShopId().evaluate(baseUrlRef)
    val (d_page_id: Int, page_type_id: Int, d_page_value: String, d_page_level_id: Int) = dimPage.get(pageId.toString).getOrElse(0, 0, "", 0)
    val pageLevelId = d_page_level_id

    val pageLevel2Value = getPageLevel2Value(pageId.toString, shopId, baseUrl)
    val refPageLevel2Value = getPageLevel2Value(pageId.toString, shopId, baseUrlRef)
    val eventLevel2Vlue = ""

    val location = ""
    val ctag = ""
    val rule_id = ""
    val test_id = ""
    val select_id = ""
    val jpk = 0
    val pit_type = 0
    val sortdate = ""
    val sorthour = "0"
    val lplid = "0"
    val ptplid = "0"
    val gid = ""
    val ugroup = ""
    val loadTime = "0"
    val source = ""
    val ip = ""
    val hotGoodsId = ""
    val deviceId = ""
    val to_switch = ""
    val utm = ""
    val date = ""
    val hour = ""
    val uid = ""

    val dwSiteId = getDwSiteId(baseUrl)
    val dwSessionId = getDwSessionId(baseLog.qm_session_id, baseLog.qm_jpid)

    val table_source = "h5_app_event"
    val user = User.apply(guId, uid, utm, "", dwSessionId, dwTeminalId, appVersion, dwSiteId, javaToScalaInt(refSiteId), ctag, location, jpk, ugroup, date, hour)
    val pe = PageAndEvent.apply(javaToScalaInt(pageId), pageValue, javaToScalaInt(refPageId), refPageValue, shopId, refShopId, pageLevelId, "0", "0", hotGoodsId, pageLevel2Value, refPageLevel2Value, pit_type, sortdate, sorthour, lplid, ptplid, gid, table_source)
    val page = Page.apply(source, ip, "", "", deviceId, to_switch)
    val event = Event.apply(eventId.toString(), eventValue, eventLevel2Vlue, rule_id, test_id, select_id, loadTime)

    (user, pe, page, event)
  }
    // 解析pc wap wx 端数据
  def parsePcWapWx (dimEvent: mutable.HashMap[String, (Int, Int)],
                    dimPage: mutable.HashMap[String, (Int, Int, String, Int)],
                    baseLog: BaseLog,
                    eventJoinKey: String,
                    sid: String): (User, PageAndEvent, Page, Event) = {

    val baseUrlRef = baseLog.baseUrlRef
    val baseUrl = baseLog.baseUrl
    val qm_device_id = baseLog.qm_device_id

    val baseTerminalId = getTerminalIdFromBase(qm_device_id, baseUrl)
    val dwTeminalId = getTerminalIdForPC(baseTerminalId)
    val (d_event_id: Int, event_type_id: Int) = dimEvent.get(eventJoinKey).getOrElse(0, 0)
    val eventId = d_event_id match {
      case a if a > 0 => a
      case _ => 0
    }

    val dwSiteId = getDwSiteId(baseUrl)
    val ul_id = baseLog.ul_id
    val guId = ul_id
    val dwSessionId = getDwSessionId(sid, ul_id)
    val eventValue = getEventValue(event_type_id, baseLog.actionName, baseLog.eV)
    val refPageId = new GetPageID().evaluate(baseUrlRef)
    val refPageValue = new GetDwPcPageValue().evaluate(baseUrlRef)
    val refSiteId = new GetSiteId().evaluate(baseUrlRef)
    // hive-udf Decoding函数
    val userId = Decoding.evaluate(baseLog.s_uid)
    val pageId = new GetPageID().evaluate(baseUrl)
    val pageValue = new GetDwPcPageValue().evaluate(baseUrl)
    val shopId = new GetShopId().evaluate(baseUrl)
    val refShopId = new GetShopId().evaluate(baseUrlRef)
    val starttime = baseLog.timeStamp
    val endtime = baseLog.timeStamp

    val table_source = "h5_app_event"

    val appVersion = ""


    // TODO 以 page_id 为key
    val (d_page_id: Int, page_type_id: Int, d_page_value: String, d_page_level_id: Int) = dimPage.get(pageId.toString).getOrElse(0, 0, "", 0)
    val pageLevelId = d_page_level_id
    val pageLevel2Value = ""

    val refPageLevel2Value = ""
    val eventLevel2Vlue = ""
    val jpk = 0
    val pit_type = 0
    val sortdate = ""
    val sorthour = "0"
    val lplid = "0"
    val ptplid = "0"
    val gid = ""
    val ugroup = ""
    val loadTime = "0"
    val source = ""
    val ip = ""
    val hotGoodsId = ""

    val ctag = ""
    val location = ""
    val deviceId = ""
    val to_switch = ""
    val date = ""
    val hour = ""

    val rule_id = ""
    val test_id = ""
    val select_id = ""

    //    (dwTeminalId, appVersion, eventId, dwSiteId, dwSessionId,)
    val user = User.apply(guId, userId.toString, baseLog.utmId, "", dwSessionId, dwTeminalId, appVersion, dwSiteId, javaToScalaInt(refSiteId), ctag, location, jpk, ugroup, date, hour)
    val pe = PageAndEvent.apply(javaToScalaInt(pageId), pageValue, javaToScalaInt(refPageId), refPageValue, shopId, refShopId, pageLevelId, "0", "0", hotGoodsId, pageLevel2Value, refPageLevel2Value, pit_type, sortdate, sorthour, lplid, ptplid, gid, table_source)
    val page = Page.apply(source, ip, "", "", deviceId, to_switch)
    val event = Event.apply(eventId.toString(), eventValue, eventLevel2Vlue, rule_id, test_id, select_id, loadTime)

    (user, pe, page, event)
  }

  def getDwSessionId(sId: String, guId: String): String = {
    //  -- H5页面的session_id通过cookie中捕获APP的session_id获取
    val dwSessionId = if (!sId.isEmpty) {
      val res = sId match {
        case "null" => guId
        case x if x.length > 0 => x
        case _ => guId
      }
      res
    } else {
      guId
    }
    dwSessionId
  }


  def getTerminalIdFromBase(qm_device_id: String, url: String): Int = {
    import scala.util.matching._
    val reg = new Regex("""http(s?)://(tuan|wx).*""")
    val terminalId = if ("MicroMessenger".equals(qm_device_id)) {
      val res = url match {
        case reg(x, y) => 6
        case _ => 1
      }
      res
    } else {
      val reg = new Regex("""http(s?)://(tuan|m).*""")
      val res = url match {
        case reg(x, y) => 2
        case _ => 1
      }
      res
    }
    terminalId
  }

  def getTerminalIdForPC(terminalId: Int): Int = {
    terminalId match {
      case 1 => ScalaConstants.T_PC
      case 2 => ScalaConstants.T_Wap
      case 6 => ScalaConstants.T_WeiXin
      case _ => ScalaConstants.T_Unknow
    }
  }

  def getTerminalIdForH5(qmDeviceId: String): Int = {
    qmDeviceId.length match {
      case 14 | 15 => ScalaConstants.T_Android
      case 36 => ScalaConstants.T_IOS
      case _ => ScalaConstants.T_Unknow
    }
  }

  def getPageValue(urlPageId: Int, url: String): String = {
    val upa = Array(12, 14, 25, 26, 28, 29)
    val pageValue = urlPageId match {
      case 33 => new GetKeyWord().evaluate(url)
      case x if (upa.exists({ x: Int => x == urlPageId })) => new GetKeyWord().evaluate(url)
      case _ => ""
    }
    pageValue
  }

  /**
    * WHEN b.event_type_id = 10 THEN concat(action_name,'::',getdwpcpagevalue(split(a.event_value,'::')[1]),'::',split(a.event_value,'::')[0])
    * @param event_type_id
    * @param actionName
    * @param e_v
    * @return
    */
  def getEventValue(event_type_id: Int, actionName: String, e_v: String): String = {
    val eventValue = if (event_type_id == 10) {
      val ev1 = e_v.split("::")(1)
      val res = new GetDwPcPageValue().evaluate(ev1)
      val ev0 = e_v.split("::")(0)
      actionName + "::" + res + "::" + ev0
    } else {
      e_v
    }
    eventValue
  }


}


object H5EventTransformer {

  def main(args: Array[String]): Unit = {
    val h5 = new H5EventTransformer()

    val urlPageId = 29
    val upa = Array(12, 14, 25, 26, 28, 29)
    val res = urlPageId match {
      case 33 => "aaaa"
      case x if (upa.exists({ x: Int => x == urlPageId })) => "bbbb"
      case _ => ""
    }
    println(res)
  }
}