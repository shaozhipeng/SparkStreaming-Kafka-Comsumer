-- 此方案是ok的
use {$dbName};
alter table {$tableName} add partition (date="{$date}", gu_hash="0") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=0/';
alter table {$tableName} add partition (date="{$date}", gu_hash="1") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=1/';
alter table {$tableName} add partition (date="{$date}", gu_hash="2") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=2/';
alter table {$tableName} add partition (date="{$date}", gu_hash="3") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=3/';
alter table {$tableName} add partition (date="{$date}", gu_hash="4") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=4/';
alter table {$tableName} add partition (date="{$date}", gu_hash="5") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=5/';
alter table {$tableName} add partition (date="{$date}", gu_hash="6") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=6/';
alter table {$tableName} add partition (date="{$date}", gu_hash="7") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=7/';
alter table {$tableName} add partition (date="{$date}", gu_hash="8") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=8/';
alter table {$tableName} add partition (date="{$date}", gu_hash="9") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=9/';
alter table {$tableName} add partition (date="{$date}", gu_hash="a") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=a/';
alter table {$tableName} add partition (date="{$date}", gu_hash="b") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=b/';
alter table {$tableName} add partition (date="{$date}", gu_hash="c") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=c/';
alter table {$tableName} add partition (date="{$date}", gu_hash="d") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=d/';
alter table {$tableName} add partition (date="{$date}", gu_hash="e") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=e/';
alter table {$tableName} add partition (date="{$date}", gu_hash="f") location '/user/hadoop/{$DataPath}/dw_real_path_list/date={$date}/gu_hash=f/';