package com.yxoct.mail.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduledTaskLeaseMapper {

  @Update(
      """
      UPDATE scheduled_task_lease
      SET owner_id = #{ownerId},
          lease_until = #{leaseUntil},
          updated_at = #{now}
      WHERE task_name = #{taskName}
        AND (lease_until <= #{now} OR owner_id = #{ownerId})
      """)
  int updateLease(
      @Param("taskName") String taskName,
      @Param("ownerId") String ownerId,
      @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);

  @Insert(
      """
      INSERT INTO scheduled_task_lease (task_name, owner_id, lease_until, updated_at)
      VALUES (#{taskName}, #{ownerId}, #{leaseUntil}, #{now})
      """)
  int insertLease(
      @Param("taskName") String taskName,
      @Param("ownerId") String ownerId,
      @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);
}
