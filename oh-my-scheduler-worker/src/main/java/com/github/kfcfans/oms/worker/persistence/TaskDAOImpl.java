package com.github.kfcfans.oms.worker.persistence;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.sql.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 任务持久化实现层，表名：task_info
 *
 * @author tjq
 * @since 2020/3/17
 */
@Slf4j
public class TaskDAOImpl implements TaskDAO {

    @Override
    public void initTable() throws Exception {

        String delTableSQL = "drop table if exists task_info";
        String createTableSQL = "create table task_info (task_id varchar(20), instance_id varchar(20), job_id varchar(20), task_name varchar(20), task_content blob, address varchar(20), status int(11), result text, failed_cnt int(11), created_time bigint(20), last_modified_time bigint(20), unique KEY pkey (instance_id, task_id))";

        try (Connection conn = ConnectionFactory.getConnection(); Statement stat = conn.createStatement()) {
            stat.execute(delTableSQL);
            stat.execute(createTableSQL);
        }
    }

    @Override
    public boolean save(TaskDO task) {
        String insertSQL = "insert into task_info(task_id, instance_id, job_id, task_name, task_content, address, status, result, failed_cnt, created_time, last_modified_time) values (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(insertSQL)) {
            fillInsertPreparedStatement(task, ps);
            return ps.executeUpdate() == 1;
        }catch (Exception e) {
            log.error("[TaskDAO] insert failed.", e);
        }
        return false;
    }

    @Override
    public boolean batchSave(Collection<TaskDO> tasks) {
        String insertSQL = "insert into task_info(task_id, instance_id, job_id, task_name, task_content, address, status, result, failed_cnt, created_time, last_modified_time) values (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(insertSQL)) {

            for (TaskDO task : tasks) {

                fillInsertPreparedStatement(task, ps);
                ps.addBatch();
            }

            ps.executeBatch();
            return true;

        }catch (Exception e) {
            log.error("[TaskDAO] insert failed.", e);
        }
        return false;
    }


    @Override
    public int batchDelete(String instanceId, List<String> taskIds) {
        String deleteSQL = "delete from task_info where instance_id = '%s' and task_id in %s";
        String sql = String.format(deleteSQL, instanceId, getInStringCondition(taskIds));
        try (Connection conn = ConnectionFactory.getConnection(); Statement stat = conn.createStatement()) {

            return stat.executeUpdate(sql);

        }catch (Exception e) {
            log.error("[TaskDAO] batchDelete failed(instanceId = {}, taskIds = {}).", instanceId, taskIds, e);
        }
        return 0;
    }

    @Override
    public List<TaskDO> simpleQuery(SimpleTaskQuery query) {
        ResultSet rs = null;
        String sql = "select * from task_info where " + query.getQueryCondition();
        List<TaskDO> result = Lists.newLinkedList();
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            rs = ps.executeQuery();
            while (rs.next()) {
                result.add(convert(rs));
            }
        }catch (Exception e) {
            log.error("[TaskDAO] simpleQuery failed(sql = {}).", sql, e);
        }finally {
            if (rs != null) {
                try {
                    rs.close();
                }catch (Exception ignore) {
                }
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> simpleQueryPlus(SimpleTaskQuery query) {
        ResultSet rs = null;
        String sqlFormat = "select %s from task_info where %s";
        String sql = String.format(sqlFormat, query.getQueryContent(), query.getQueryCondition());
        List<Map<String, Object>> result = Lists.newLinkedList();
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            rs = ps.executeQuery();
            // 原数据，包含了列名
            ResultSetMetaData  metaData = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = Maps.newHashMap();
                result.add(row);

                for (int i = 0; i < metaData.getColumnCount(); i++) {
                    String colName = metaData.getColumnName(i + 1);
                    Object colValue = rs.getObject(colName);
                    row.put(colName, colValue);
                }
            }
        }catch (Exception e) {
            log.error("[TaskDAO] simpleQuery failed(sql = {}).", sql, e);
        }finally {
            if (rs != null) {
                try {
                    rs.close();
                }catch (Exception ignore) {
                }
            }
        }
        return result;
    }

    @Override
    public boolean simpleUpdate(SimpleTaskQuery condition, TaskDO updateField) {
        String sqlFormat = "update task_info set %s where %s";
        String updateSQL = String.format(sqlFormat, updateField.getUpdateSQL(), condition.getQueryCondition());
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement stat = conn.prepareStatement(updateSQL)) {
            stat.executeUpdate();
            return true;
        }catch (Exception e) {
            log.error("[TaskDAO] simpleUpdate failed(sql = {}).", updateField, e);
            return false;
        }
    }

    @Override
    public Map<String, String> queryTaskId2TaskResult(String instanceId) {
        ResultSet rs = null;
        Map<String, String> taskId2Result = Maps.newLinkedHashMapWithExpectedSize(4096);
        String sql = "select task_id, result from task_info where instance_id = ?";
        try (Connection conn = ConnectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            rs = ps.executeQuery();
            while (rs.next()) {
                taskId2Result.put(rs.getString("task_id"), rs.getString("result"));
            }
        }catch (Exception e) {
            log.error("[TaskDAO] queryTaskId2TaskResult failed(sql = {}).", sql, e);
        }finally {
            if (rs != null) {
                try {
                    rs.close();
                }catch (Exception ignore) {
                }
            }
        }
        return taskId2Result;
    }

    private static TaskDO convert(ResultSet rs) throws SQLException {
        TaskDO task = new TaskDO();
        task.setTaskId(rs.getString("task_id"));
        task.setInstanceId(rs.getString("instance_id"));
        task.setJobId(rs.getString("job_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setTaskContent(rs.getBytes("task_content"));
        task.setAddress(rs.getString("address"));
        task.setStatus(rs.getInt("status"));
        task.setResult(rs.getString("result"));
        task.setFailedCnt(rs.getInt("failed_cnt"));
        task.setCreatedTime(rs.getLong("created_time"));
        task.setLastModifiedTime(rs.getLong("last_modified_time"));
        return task;
    }

    private static void fillInsertPreparedStatement(TaskDO task, PreparedStatement ps) throws SQLException {
        ps.setString(1, task.getTaskId());
        ps.setString(2, task.getInstanceId());
        ps.setString(3, task.getJobId());
        ps.setString(4, task.getTaskName());
        ps.setBytes(5, task.getTaskContent());
        ps.setString(6, task.getAddress());
        ps.setInt(7, task.getStatus());
        ps.setString(8, task.getResult());
        ps.setInt(9, task.getFailedCnt());
        ps.setLong(10, task.getCreatedTime());
        ps.setLong(11, task.getLastModifiedTime());
    }

    private static String getInStringCondition(Collection<String> collection) {
        if (CollectionUtils.isEmpty(collection)) {
            return "()";
        }
        StringBuilder sb = new StringBuilder(" ( ");
        collection.forEach(str -> sb.append("'").append(str).append("',"));
        return sb.replace(sb.length() -1, sb.length(), " ) ").toString();
    }
}