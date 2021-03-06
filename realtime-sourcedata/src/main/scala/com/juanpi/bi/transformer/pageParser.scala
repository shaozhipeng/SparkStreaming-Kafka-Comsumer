package com.juanpi.bi.transformer

import java.util.regex.Pattern

import com.juanpi.hive.udf._
import play.api.libs.json.{JsNull, Json}

/**
  * Created by gongzi on 2016/9/30.
  */
object pageParser {


  /**
    * 二级页面值(品牌页：引流款ID；商品skc、分享回流标识)
    *
    * @param x_page_id
    * @param x_extend_params
    * @param server_jsonstr
    * @param url
    * @return
    */
  def getPageLvl2Value(x_page_id: Int, x_extend_params: String, server_jsonstr: String, url: String): String = {
    val strValue = pageAndEventParser.getParsedJson(server_jsonstr)
    val page_lel2_value =
      if(x_page_id == 250 && x_extend_params.nonEmpty
        && x_extend_params.contains("_")
        && x_extend_params.split("_").length > 2)
      {
        // 解析品牌页的引流款商品
        new GetGoodsId().evaluate(x_extend_params.split("_")(2))
      }
      else if(x_page_id == 154 || x_page_id == 289) {
        val pid = new GetPageID().evaluate(x_extend_params)
        val pageId = if(pid == null) {0} else pid.toInt

        if(pageId == 10104) {
          new GetSkcId().evaluate(x_extend_params)
        }
        else if(pid == 10102) {
          new GetShopId().evaluate(x_extend_params)
        } else ""
      }
      else if(x_page_id == 169
        && !strValue.equals(JsNull)
        && server_jsonstr.contains("order_status")) {
        (strValue \ "order_status").toString()
      } else if(url.nonEmpty) {
        url match {
          case a if a.contains("singlemessage") => "singlemessage"
          case b if b.contains("groupmessage") => "groupmessage"
          case c if c.contains("timeline") => "timeline"
          case d if d.contains("ptsy_1") => "ptsy_1"
          case e if e.contains("ggmk_1") => "ggmk_1"
          case f if f.contains("push_app") => "push_app"
          case g if g.contains("push_wx") => "push_wx"
          case h if h.contains("ggmk_2") => "ggmk_2"
          case _ => ""
        }
      }
      else {""}
    page_lel2_value
  }


  /**
    *
    * @param x_page_id
    * @param x_extend_params
    * @param page_type_id
    * @param x_page_value
    * @return
    */
  def getPageValue(x_page_id:Int, x_extend_params: String, url: String, page_type_id: Int, x_page_value: String): String = {
    // 解析 page_value

    val pid = new GetPageID().evaluate(url)

    val pageId = if(pid == null) {0} else pid.toInt

    val page_value: String =
      if ((x_page_id == 289 || x_page_id == 154 || x_page_id == 254) && pageId > 0) {
      val res = new GetDwPcPageValue().evaluate(url)
      res
      }
      else
      {
        val param = if (x_page_id == 254) {
          x_extend_params
        } else if (page_type_id == 1 || page_type_id == 4 || page_type_id == 10) {
          x_page_value
        } else if (x_page_id == 250) {
          // app端品牌页面id = 250, page_extends_param 格式：加密brandid_shopid_引流款id,或者 加密brandid_shopid
          val goodsId = new GetGoodsId().evaluate(x_extend_params.split("_")(0))
          goodsId
        } else {
          x_extend_params
        }

        val res = new GetDwMbPageValue().evaluate(param, page_type_id.toString)

        res
      }
      page_value
  }

  /**
    *
    * @param pageName
    * @param extendParams
    * @param serverJsonStr
    * @return
    */
  def forPageId(pageName: String, extendParams: String, serverJsonStr: String): String = {
    val strValue = pageAndEventParser.getParsedJson(serverJsonStr)
    val forPageId = pageName.toLowerCase() match {
      case a if pageName.toLowerCase() == "page_tab"
        && pageAndEventParser.isInteger(extendParams)
        && (extendParams.toLong > 0
        && extendParams.toLong < 9999999) => "page_tab"
      case c if pageName.toLowerCase() == "page_tab"
        && !strValue.equals(JsNull)
        && (strValue \ "cid").asOpt[Int].getOrElse(0) < 0
      => (pageName+(strValue \ "cid").asOpt[String]).toLowerCase()
      case b if pageName.toLowerCase() != "page_tab" => pageName.toLowerCase()
      case _ => (pageName+extendParams).toLowerCase()
    }
    forPageId
  }

  /**
    * 从sever_jsonstr解析得到select_id,test_id,rule_id
    * @param serverjsongStr
    * @return
    */
  def getAbInfo(serverjsongStr: String):(String, String, String) = {
    val (rule_id, test_id, select_id) = if(serverjsongStr.contains("ab_info")) {
      val sJsonStr = Json.parse(serverjsongStr)
      val ab_info = (sJsonStr \ "ab_info").asOpt[String].getOrElse("")
      val pattern: Pattern = Pattern.compile("^-?[1-9]\\d*$")
      if (ab_info.isEmpty) {
        ("","","")
      } else if (pattern.matcher(ab_info.toString.split("_")(2)).matches() && pattern.matcher(ab_info.toString.split("_")(1)).matches() && pattern.matcher(ab_info.toString.split("_")(0)).matches()) {
        (ab_info.toString.split("_")(2),ab_info.toString.split("_")(0),ab_info.toString.split("_")(1))
      } else {
        ("","","")
      }
    } else {
      ("","","")
    }

    (rule_id, test_id, select_id)
  }


  // 测试test_id,select_id和rule_id是否能解出
    def main(args: Array[String]): Unit = {
      val line = """{"app_name":"zhe","app_version":"4.2.4","c_label":"C2","c_server":"{\"gid\":\"C2\",\"ugroup\":\"668_649_684_486_485_453_574_573_518_605_516_603_696_695_581_652_478_496_653_523_494_544_377_614_584_711_616_618_449_593\"}","deviceid":"869411025179622","endtime":"1487898778265","endtime_origin":"1487898777452","extend_params":"2","ip":"175.20.230.7","jpid":"00000000-011c-d810-9b17-c31d00a46c2d","location":"吉林省吉林市蛟河市005乡道靠近白石山信用社","os":"android","os_version":"4.4.4","pagename":"page_message_content","pre_extend_params":"","pre_page":"page_message","server_jsonstr":"{\"ab_info\":\"1_2_3\"}","session_id":"1461916191529_zhe_1487898757041","source":"","starttime":"1487898768991","starttime_origin":"1487898768178","ticks":"1461916191529","to_switch":"0","uid":"36541281","utm":"101221","wap_pre_url":"","wap_url":""}"""
      val row = Json.parse(line)
      val server_jsonstr = (row \ "server_jsonstr").asOpt[String].getOrElse("")
      val abinfo = pageParser.getAbInfo(server_jsonstr)
    println(abinfo)
    println("rule_id为"+abinfo._1)
    println("test_id为"+abinfo._2)
    println("select_id为"+abinfo._3)
  }
}
