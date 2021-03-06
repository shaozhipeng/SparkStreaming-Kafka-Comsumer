package com.juanpi.bi.transformer

import java.util.regex.{Matcher, Pattern}

import com.juanpi.hive.udf.{GetDwMbPageValue, GetDwPcPageValue, GetGoodsId, GetPageID}
import play.api.libs.json.{JsNull, Json}

/**
  * Created by gongzi on 2016/9/28.
  */
object eventParser {

  def getForPageId(cid: String, f_page_extend_params: String, pagename: String): String = {
    val for_pageid = if("-1".equals(cid)) {
      "page_taball"
    } else if ("-2".equals(cid)) {
      "page_tabpast_zhe"
    } else if ("-3".equals(cid)) {
      "page_tabcrazy_zhe"
    } else if ("-4".equals(cid)) {
      "page_tabjiu"
    } else if ("-5".equals(cid) || "-6".equals(cid)) {
      "page_tabyugao"
    } else if ((!cid.isEmpty && cid.toInt > 0) | (cid == "-100" && (f_page_extend_params == "10045" || f_page_extend_params == "100105"))) {
      // when cast(get_json_object(server_jsonstr, '$.cid') as int) > 0 or (cast(get_json_object(server_jsonstr, '$.cid') as int) = -100 and page_extends_param in (10045,100105)) then 'page_tab'
      "page_tab"
    } else if ((cid == "0") && List("all", "past_zhe", "crazy_zhe", "jiu", "yugao").contains(f_page_extend_params)) {
      ""
    } else if ("page_h5".equals(pagename)) {
      val pid = new GetPageID().evaluate(f_page_extend_params)
      val pageId = if(pid == null) {0} else pid.toInt
      if (pageId > 0) { "page_active" } else (pagename + f_page_extend_params).toLowerCase()
    } else if (!"page_tab".equals(pagename)) {
      pagename
    } else {
      (pagename + f_page_extend_params).toLowerCase()
    }
    for_pageid
  }

  /**
    *
    * @param x_page_id
    * @param x_extends_param
    * @return
    */
  def getPageLvl2Value(x_page_id: Int, x_extends_param: String): String = {
    val page_lel2_value =
      if(x_page_id == 250 && x_extends_param.nonEmpty
        && x_extends_param.contains("_")
        && x_extends_param.split("_").length > 2)
      {
        // 解析品牌页的引流款商品
        new GetGoodsId().evaluate(x_extends_param.split("_")(2))
      }
     else {""}
    page_lel2_value
  }

  // outlierData 离群数据过滤
  /**
    * 如果 page_name="page_tab",并且 cid 为空，且f_page_extend_params不在("all", "past_zhe", "crazy_zhe", "jiu", "yugao")之中，就过滤掉
    * 如果 pageName = "page_h5" 且 pid = -1
    * @param pageName
    * @param cid
    * @param fctPageExtendParams
    * @return
    */
  def filterOutlierPageId(activityName: String, pageName: String, cid: String, fctPageExtendParams: String): Boolean = {
    val flag = if(pageName.isEmpty || activityName.isEmpty) {
      true
    }
    else if(activityName.contains("exposure_") || activityName.contains("_performance") || "collect_page_h5".equals(activityName)){
      // 过滤event中的曝光数据：exposure_ad_popup, exposure_ad_inscreen
      // 和性能采集数据collect_data_performance, collect_page_performance
      true
    }
    else if("page_tab".equals(pageName)
      && cid.isEmpty
      && !List("all", "past_zhe", "crazy_zhe", "jiu", "yugao").contains(fctPageExtendParams)) {
      true
    } else if("page_h5".equals(pageName)) {
      val pid = new GetPageID().evaluate(fctPageExtendParams)
      val pageId = if(pid == null) {0} else pid.toInt
      pageId match {
        case -1 => true
        case _ => false
      }
    } else false
    flag
  }

  def getForPrePageId(pagename: String, f_pre_extend_params: String, pre_page: String):String = {
    val forPrePageId =
      if ("page_h5".equals(pagename)) {
        val pid = new GetPageID().evaluate(f_pre_extend_params)
        val pageId = if(pid == null) {0} else pid.toInt
        if (pageId > 0) {
          "page_active"
        } else {
          (pagename + f_pre_extend_params).toLowerCase()
        }
      } else if (!"page_tab".equals(pre_page)) {
        pre_page.toLowerCase()
      }
      else {
        (pre_page + f_pre_extend_params).toLowerCase()
      }
    forPrePageId
  }

  def getForEventId(cid: String, activityname: String, t_extend_params: String): String = {
    val forEventId = if("-6".equals(cid)) {
      "click_yugao_recommendation"
    } else if("-100".equals(cid)) {
      "click_shoppingbag_recommendation"
    } else if("-101".equals(cid)) {
      "click_orderdetails_recommendation"
    } else if ("-102".equals(cid)) {
      "click_detail_recommendation"
    } else {
      activityname.toLowerCase
    }
    forEventId
  }

  /**
    *
    * @param activityname
    * @param t_extend_params
    * @param server_jsonstr
    * @return
    */
  def getForExtendParams(activityname: String, t_extend_params: String,server_jsonstr: String): String = {

    val f_extend_params = if(server_jsonstr.contains("pit_info") && pageAndEventParser.getJsonValueByKey(server_jsonstr, "pit_info").nonEmpty) {
      pageAndEventParser.getJsonValueByKey(server_jsonstr, "pit_info")
    } else if(t_extend_params.contains("pit_info") && pageAndEventParser.getJsonValueByKey(t_extend_params, "pit_info").nonEmpty){
      pageAndEventParser.getJsonValueByKey(t_extend_params, "pit_info")
    } else if(server_jsonstr.contains("ads_id") && pageAndEventParser.getJsonValueByKey(server_jsonstr, "ads_id").nonEmpty) {
      pageAndEventParser.getJsonValueByKey(server_jsonstr, "ads_id")
    } else if (activityname.equals("click_cube_block") && !server_jsonstr.equals("{}")) {
      server_jsonstr
    } else {
      t_extend_params
    }
    f_extend_params
  }

  /**
    * 从base层解析 extendparams
    * @param activityname
    * @param extend_params
    * @param app_version
    * @return
    */
  def getExtendParamsFromBase(activityname: String, extend_params: String, app_version: String): String = {
    // 老版本 3.2.3
    val app_version323 = 323
    activityname match {
      case "click_temai_inpage_qq" => new GetGoodsId().evaluate(extend_params)
      case "click_temai_returngoods" => new GetGoodsId().evaluate(extend_params)
      case "click_temai_inpage_share" => new GetGoodsId().evaluate(extend_params)
      case "click_temai_inpage_collect" => new GetGoodsId().evaluate(extend_params)
      case "click_temai_inpage_cancelcollect" => new GetGoodsId().evaluate(extend_params)
      case "click_temai_orderdetails_complex" => new GetGoodsId().evaluate(extend_params)
      case "click_goods_tb" => new GetGoodsId().evaluate(extend_params)
      case "click_goods_cancel" => new GetGoodsId().evaluate(extend_params)
      case "click_goods_collection" => new GetGoodsId().evaluate(extend_params)
      case "click_goods_share" => new GetGoodsId().evaluate(extend_params)
      case a if "click_temai_inpage_joinbag".equals(activityname) && getVersionNum(app_version) < app_version323 => new GetGoodsId().evaluate(extend_params)
      case _ => extend_params.toLowerCase()
    }
  }

  def getEventId(d_event_id: Int, app_version: String): Int = {
    val app_ver = getVersionNum(app_version)
    val eid = d_event_id match {
      case a if (d_event_id == 0) => -1
      case b if (app_ver > 323 || d_event_id != 279) => d_event_id
      case _ => -999
    }
    eid
  }

  /**
    *
    * @param x_page_id
    * @param x_extend_params
    * @param page_type_id
    * @param x_page_value
    * @return
    */
  def getPageValue(x_page_id:Int, x_extend_params: String, cid: String, page_type_id: Int, x_page_value: String): String = {
    // 解析 page_value
    val page_value: String =
    if (x_page_id == 289 || x_page_id == 154) {
      val res = new GetDwPcPageValue().evaluate(x_extend_params)
      res
    } else {
      val param = if(x_page_id == 254)
      {
        if("10045".equals(x_extend_params) || "100105".equals(x_extend_params)) {
          x_extend_params
        } else {
          cid
        }
      } else if(page_type_id == 1 || page_type_id == 4 || page_type_id == 10) {
        x_page_value
      } else if(x_page_id == 250) {
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

  def getEventValue(event_type_id: Int, activityname: String, extend_params: String, server_jsonstr: String): String =
  {
    val operTime = pageAndEventParser.getJsonValueByKey(server_jsonstr, "_t")

    if (event_type_id == 10) {
      if (activityname.contains("click_cube")) {
        extend_params
      } else if (!server_jsonstr.contains("_t") || operTime.isEmpty) {
        ""
      } else {
        extend_params
      }
    } else {
      extend_params
    }
  }

  /**
    * ab测试，选择A还是B
    * @param server_jsonstr
    * @return
    */
  def getAbinfo(server_jsonstr: String): (String, String) = {
    var selectId:String = ""
    var testId:String = ""
    if (server_jsonstr.contains("ab_info")) {
      val ab_info = pageAndEventParser.getJsonValueByKey(server_jsonstr, "ab_info")
      val pat = Pattern.compile("([A-Z])([0-9]\\d*)")
      val mch = pat.matcher(ab_info)
      if(mch.find()){
        selectId = mch.group(1)
        testId = mch.group(2)
      } else {
        selectId = pageAndEventParser.getJsonValueByKey(ab_info, "select")
        testId = pageAndEventParser.getJsonValueByKey(ab_info, "test_id")
      }
      (selectId, testId)
    } else ("", "")
  }

  /**
    *
    * @param gsort_key
    * @return
    */
  def getGsortKey(gsort_key: String): (String, String, String, String) = {
    val defaultPat = Pattern.compile("_SORT_(-?[0-9]\\d*)_(-?[0-9]\\d*)_(-?[0-9]\\d*)_(-?[0-9]\\d*)")
    val positionPat = Pattern.compile("POSTION_SORT_(-?[0-9]\\d*)_(-?[0-9]\\d*)_(-?[0-9]\\d*)_(-?[0-9]\\d*)_(-?[0-9]\\d*)")
    val mch: Matcher = defaultPat.matcher(gsort_key)
    if(mch.find()) {
      val sortdate = mch.group(2)
      val sorthour = mch.group(3)
      val lplid = mch.group(4)
      val pMch: Matcher = positionPat.matcher(gsort_key)
      val ptplid: String = if(pMch.find()) {
        pMch.group(3)
      }
      else ""
      (sortdate, sorthour, lplid, ptplid)
    }
    else ("", "", "", "")
  }

  /**
    * 过滤函数，满足条件的留下，不满足的过滤掉
    * @param line
    * @return
    */
  def filterFunc(line: String): Boolean = {
    val row = Json.parse(line)
    val activityName = (row \ "activityname").asOpt[String].getOrElse("").toLowerCase()
    val blackArray = Array("exposure_temai_pic", "collect_mainpage_loadtime", "exposure_ad_welt", "collect_popup_unlock", "crash_exception_info", "exposure_ad_inscreen", "exposure_ad_popup_sec", "exposure_ad_popup", "show_temai_pay_applepay", "collect_api_responsetime", "collect_page_h5", "collect_data_performance", "collect_page_performance")
    val isKeep =
      if(activityName.contains("_performance")) {
        false
      } else {
        // 包含上述
        !blackArray.exists(_ == activityName)
      }
    // 满足条件的留下，不满足的过滤掉
    isKeep
  }


  /**
    *
    * @param server_jsonstr
    * @return
    */
  def getGsortPit(server_jsonstr: String): (Int, String) = {
    val js_server_jsonstr = pageAndEventParser.getParsedJson(server_jsonstr)
    if (!js_server_jsonstr.equals(JsNull) && !server_jsonstr.equals("{}")) {
      val pit_type = (js_server_jsonstr \ "_pit_type").asOpt[Int].getOrElse(0)
      val gsort_key = (js_server_jsonstr \ "_gsort_key").asOpt[String].getOrElse("")
      (pit_type, gsort_key)
    } else {
      (0, "")
    }
  }

  def getVersionNum(app_version: String): Int = {
    // TODO
    val resInt = if (app_version.nonEmpty) {
      app_version.replace(".", "").toInt
    }
    else {
      0
    }
    resInt
  }

  def main(args: Array[String]): Unit = {
//    val line = """{"app_name":"zhe","app_version":"4.2.4","c_label":"C2","c_server":"{\"gid\":\"C2\",\"ugroup\":\"668_649_684_486_485_453_574_573_518_605_516_603_696_695_581_652_478_496_653_523_494_544_377_614_584_711_616_618_449_593\"}","deviceid":"869411025179622","endtime":"1487898778265","endtime_origin":"1487898777452","extend_params":"2","ip":"175.20.230.7","jpid":"00000000-011c-d810-9b17-c31d00a46c2d","location":"吉林省吉林市蛟河市005乡道靠近白石山信用社","os":"android","os_version":"4.4.4","pagename":"page_message_content","pre_extend_params":"","pre_page":"page_message","server_jsonstr":"{\"ab_info\":\"1_2_3\"}","session_id":"1461916191529_zhe_1487898757041","source":"","starttime":"1487898768991","starttime_origin":"1487898768178","ticks":"1461916191529","to_switch":"0","uid":"36541281","utm":"101221","wap_pre_url":"","wap_url":""}"""
//    val row = Json.parse(line)
//    println(row)
//    val server_jsonstr = (row \ "server_jsonstr").asOpt[String].getOrElse("")
//    val abinfo = pageParser.getAbInfo(server_jsonstr)
//    println(abinfo)
//    println("rule_id为"+abinfo._1)
//    println("test_id为"+abinfo._2)
//    println("select_id为"+abinfo._3)


    val l_server_jsonstr = """{"activityname":"click_cube_block","app_name":"zhe","app_version":"4.2.2","c_label":"","c_server":"","cube_position":"1_4","deviceid":"868146027911231","endtime":"1491372673338","endtime_origin":"1491372672813","extend_params":"","ip":"111.58.146.136","jpid":"ffffffff-dc32-eec5-90b8-16f33882f5ca","location":"","os":"android","os_version":"5.1.1","page_extends_param":"312","pagename":"page_tab","pre_extends_param":"1246","pre_page":"page_tab","result":"1","server_jsonstr":"{\"pit_info\":\"ad_id::291::block_id::3199::img_id::9115::1_4\",\"cid\":\"312\",\"_t\":1491372137,\"_z\":\"5\",\"ab_info\":null}","session_id":"0_zhe_1491372092479","source":"","starttime":"1491372673338","starttime_origin":"1491372672813","ticks":"0","to_switch":"0","uid":"0","utm":"106872"}"""
    val rows = Json.parse(l_server_jsonstr)
    val server_jsonstr = (rows \ "server_jsonstr").asOpt[String].getOrElse("")
    println(getForExtendParams("click_cube_block", "", server_jsonstr))
  }
}
