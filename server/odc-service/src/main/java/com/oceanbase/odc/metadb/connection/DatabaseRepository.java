/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.metadb.connection;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.oceanbase.odc.service.db.schema.model.DBObjectSyncStatus;

public interface DatabaseRepository extends JpaRepository<DatabaseEntity, Long>,
        JpaSpecificationExecutor<DatabaseEntity> {
    Optional<DatabaseEntity> findByConnectionIdAndNameAndExisted(Long connectionId, String name, Boolean existed);

    List<DatabaseEntity> findByConnectionIdInAndNameAndExisted(Collection<Long> connectionIds, String name,
            Boolean existed);

    List<DatabaseEntity> findByConnectionId(Long connectionId);

    List<DatabaseEntity> findByConnectionIdIn(Collection<Long> connectionIds);

    List<DatabaseEntity> findByConnectionIdAndExisted(Long connectionId, Boolean existed);

    List<DatabaseEntity> findByProjectId(Long projectId);

    List<DatabaseEntity> findByProjectIdIn(Collection<Long> projectIds);

    List<DatabaseEntity> findByProjectIdAndExisted(Long projectId, Boolean existed);

    List<DatabaseEntity> findByProjectIdInAndExistedAndObjectSyncStatusNot(Collection<Long> projectIds, Boolean existed,
            DBObjectSyncStatus dbObjectSyncStatus);

    List<DatabaseEntity> findByOrganizationIdAndExistedAndObjectSyncStatusNot(Long organizationId, Boolean existed,
            DBObjectSyncStatus dbObjectSyncStatus);

    List<DatabaseEntity> findByIdIn(Collection<Long> ids);

    List<DatabaseEntity> findByNameIn(Collection<String> name);

    List<DatabaseEntity> findByIdInAndProjectIdIn(Collection<Long> ids, Collection<Long> projectIds);

    @Modifying
    @Transactional
    @Query(value = "delete from connect_database t where t.connection_id in (:connectionIds)", nativeQuery = true)
    int deleteByConnectionIds(@Param("connectionIds") Collection<Long> connectionIds);

    @Modifying
    @Transactional
    @Query(value = "delete from connect_database t where t.connection_id = :connectionId", nativeQuery = true)
    int deleteByConnectionId(@Param("connectionId") Long connectionId);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.is_existed = :existed where t.id in (:ids)", nativeQuery = true)
    int setExistedByIdIn(@Param("existed") Boolean existed, @Param("ids") Collection<Long> ids);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.object_sync_status = :#{#status.name()} where t.id in (:ids)",
            nativeQuery = true)
    int setObjectSyncStatusByIdIn(@Param("ids") Collection<Long> ids, @Param("status") DBObjectSyncStatus status);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.object_sync_status = :#{#status.name()} "
            + "where t.object_sync_status = :#{#originalStatus.name()} "
            + "and (t.object_last_sync_time < :syncTime or t.object_last_sync_time is null)", nativeQuery = true)
    int setObjectSyncStatusByObjectSyncStatusAndObjectLastSyncTimeIsNullOrBefore(
            @Param("status") DBObjectSyncStatus status,
            @Param("originalStatus") DBObjectSyncStatus originalStatus, @Param("syncTime") Date syncTime);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.object_sync_status = :#{#status.name()}, t.object_last_sync_time = :syncTime where t.id = :id",
            nativeQuery = true)
    int setObjectLastSyncTimeAndStatusById(@Param("id") Long id, @Param("syncTime") Date syncTime,
            @Param("status") DBObjectSyncStatus status);


    @Transactional
    @Query(value = "update `connect_database` t set t.project_id = :projectId where t.id in (:ids)", nativeQuery = true)
    @Modifying
    int setProjectIdByIdIn(@Param("projectId") Long projectId, @Param("ids") Set<Long> ids);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.is_existed = :existed where t.connection_id = :connectionId",
            nativeQuery = true)
    int setExistedByConnectionId(@Param("existed") Boolean existed, @Param("connectionId") Long connectionId);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.project_id = null where t.project_id = :projectId",
            nativeQuery = true)
    int setProjectIdToNull(@Param("projectId") Long projectId);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.environment_id = :environmentId where t.connection_id = :connectionId",
            nativeQuery = true)
    int setEnvironmentIdByConnectionId(@Param("environmentId") Long environmentId,
            @Param("connectionId") Long connectionId);

    @Modifying
    @Transactional
    @Query(value = "update connect_database t set t.remark = :remark where t.id in (:ids)", nativeQuery = true)
    int setDatabaseRemarkByIdIn(@Param("ids") Collection<Long> databaseIds, @Param("remark") String databaseRemark);
}
