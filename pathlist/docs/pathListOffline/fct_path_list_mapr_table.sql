use dw;
drop table if EXISTS  fct_path_list_mapr;
CREATE TABLE `fct_path_list_mapr`(
`page_level_id` string,
`gu_id` string,
`page_id` string,
`page_value` string,
`page_lvl2_value` string,
`event_id` string,
`event_value` string,
`event_lvl2_value` string,
`rule_id` string,
`test_id` string,
`select_id` string,
`starttime` string,
`pit_type` string,
`sortdate` string,
`sorthour` string,
`lplid` string,
`ptplid` string,
`ug_id` string
  )
PARTITIONED BY (`gu_hash` string);