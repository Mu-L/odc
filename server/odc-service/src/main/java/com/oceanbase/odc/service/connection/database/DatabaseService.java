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
package com.oceanbase.odc.service.connection.database;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import com.oceanbase.odc.common.event.LocalEventPublisher;
import com.oceanbase.odc.common.json.JsonUtils;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.authority.SecurityManager;
import com.oceanbase.odc.core.authority.permission.Permission;
import com.oceanbase.odc.core.authority.util.Authenticated;
import com.oceanbase.odc.core.authority.util.PreAuthenticate;
import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.Verify;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.constant.OrganizationType;
import com.oceanbase.odc.core.shared.constant.ResourceRoleName;
import com.oceanbase.odc.core.shared.constant.ResourceType;
import com.oceanbase.odc.core.shared.exception.AccessDeniedException;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.core.shared.exception.ConflictException;
import com.oceanbase.odc.core.shared.exception.NotFoundException;
import com.oceanbase.odc.core.shared.exception.UnexpectedException;
import com.oceanbase.odc.metadb.connection.DatabaseAccessHistoryEntity;
import com.oceanbase.odc.metadb.connection.DatabaseAccessHistoryRepository;
import com.oceanbase.odc.metadb.connection.DatabaseEntity;
import com.oceanbase.odc.metadb.connection.DatabaseRepository;
import com.oceanbase.odc.metadb.connection.DatabaseSpecs;
import com.oceanbase.odc.metadb.dbobject.DBColumnRepository;
import com.oceanbase.odc.metadb.dbobject.DBObjectRepository;
import com.oceanbase.odc.metadb.iam.PermissionRepository;
import com.oceanbase.odc.metadb.iam.UserDatabasePermissionEntity;
import com.oceanbase.odc.metadb.iam.UserDatabasePermissionRepository;
import com.oceanbase.odc.metadb.iam.UserPermissionRepository;
import com.oceanbase.odc.metadb.iam.UserTablePermissionEntity;
import com.oceanbase.odc.metadb.iam.UserTablePermissionRepository;
import com.oceanbase.odc.service.collaboration.environment.EnvironmentService;
import com.oceanbase.odc.service.collaboration.environment.model.Environment;
import com.oceanbase.odc.service.collaboration.environment.model.QueryEnvironmentParam;
import com.oceanbase.odc.service.collaboration.project.ProjectService;
import com.oceanbase.odc.service.collaboration.project.model.Project;
import com.oceanbase.odc.service.collaboration.project.model.QueryProjectParams;
import com.oceanbase.odc.service.common.model.InnerUser;
import com.oceanbase.odc.service.connection.ConnectionService;
import com.oceanbase.odc.service.connection.ConnectionSyncHistoryService;
import com.oceanbase.odc.service.connection.database.model.CreateDatabaseReq;
import com.oceanbase.odc.service.connection.database.model.DBAccessHistoryReq;
import com.oceanbase.odc.service.connection.database.model.Database;
import com.oceanbase.odc.service.connection.database.model.DatabaseSyncStatus;
import com.oceanbase.odc.service.connection.database.model.DatabaseType;
import com.oceanbase.odc.service.connection.database.model.DatabaseUser;
import com.oceanbase.odc.service.connection.database.model.DeleteDatabasesReq;
import com.oceanbase.odc.service.connection.database.model.ModifyDatabaseOwnerReq;
import com.oceanbase.odc.service.connection.database.model.QueryDatabaseParams;
import com.oceanbase.odc.service.connection.database.model.TransferDatabasesReq;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.connection.model.ConnectionSyncErrorReason;
import com.oceanbase.odc.service.connection.model.ConnectionSyncResult;
import com.oceanbase.odc.service.connection.model.InnerQueryConnectionParams;
import com.oceanbase.odc.service.db.DBSchemaService;
import com.oceanbase.odc.service.db.schema.DBSchemaSyncTaskManager;
import com.oceanbase.odc.service.db.schema.GlobalSearchProperties;
import com.oceanbase.odc.service.db.schema.model.DBObjectSyncStatus;
import com.oceanbase.odc.service.db.schema.syncer.DBSchemaSyncProperties;
import com.oceanbase.odc.service.iam.HorizontalDataPermissionValidator;
import com.oceanbase.odc.service.iam.OrganizationService;
import com.oceanbase.odc.service.iam.ProjectPermissionValidator;
import com.oceanbase.odc.service.iam.ResourceRoleService;
import com.oceanbase.odc.service.iam.UserService;
import com.oceanbase.odc.service.iam.auth.AuthenticationFacade;
import com.oceanbase.odc.service.iam.model.Organization;
import com.oceanbase.odc.service.iam.model.User;
import com.oceanbase.odc.service.iam.model.UserResourceRole;
import com.oceanbase.odc.service.monitor.datasource.GetConnectionFailedEventListener;
import com.oceanbase.odc.service.onlineschemachange.ddl.DBUser;
import com.oceanbase.odc.service.onlineschemachange.ddl.OscDBAccessor;
import com.oceanbase.odc.service.onlineschemachange.ddl.OscDBAccessorFactory;
import com.oceanbase.odc.service.onlineschemachange.rename.OscDBUserUtil;
import com.oceanbase.odc.service.permission.DBResourcePermissionHelper;
import com.oceanbase.odc.service.permission.database.model.DatabasePermissionType;
import com.oceanbase.odc.service.plugin.SchemaPluginUtil;
import com.oceanbase.odc.service.session.factory.DefaultConnectSessionFactory;
import com.oceanbase.odc.service.session.factory.OBConsoleDataSourceFactory;
import com.oceanbase.odc.service.session.model.SqlExecuteResult;
import com.oceanbase.odc.service.task.base.precheck.PreCheckTaskParameters.AuthorizedDatabase;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Lebie
 * @Date: 2023/6/5 14:34
 * @Description: []
 */
@Service
@Slf4j
@Validated
@Authenticated
public class DatabaseService {

    private final DatabaseMapper databaseMapper = DatabaseMapper.INSTANCE;

    @Autowired
    private DatabaseRepository databaseRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectPermissionValidator projectPermissionValidator;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private AuthenticationFacade authenticationFacade;

    @Autowired
    private DBSchemaService dbSchemaService;

    @Autowired
    private JdbcLockRegistry jdbcLockRegistry;

    @Autowired
    private HorizontalDataPermissionValidator horizontalDataPermissionValidator;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserDatabasePermissionRepository userDatabasePermissionRepository;

    @Autowired
    private UserTablePermissionRepository userTablePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Autowired
    private DBObjectRepository dbObjectRepository;

    @Autowired
    private DBColumnRepository dbColumnRepository;

    @Autowired
    private DBResourcePermissionHelper permissionHelper;

    @Autowired
    private ResourceRoleService resourceRoleService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityManager securityManager;

    @Autowired
    private DBSchemaSyncTaskManager dbSchemaSyncTaskManager;

    @Autowired
    private DBSchemaSyncProperties dbSchemaSyncProperties;

    @Autowired
    private GlobalSearchProperties globalSearchProperties;

    @Autowired
    private ConnectionSyncHistoryService connectionSyncHistoryService;

    @Autowired
    private DatabaseAccessHistoryRepository databaseAccessHistoryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${odc.integration.bastion.enabled:false}")
    private boolean bastionEnabled;

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public Database detail(@NonNull Long id) {
        Database database = detailSkipPermissionCheck(id);
        horizontalDataPermissionValidator.checkCurrentOrganization(database);
        if (Objects.nonNull(database.getProject()) && Objects.nonNull(database.getProject().getId())) {
            projectPermissionValidator.checkProjectRole(database.getProject().getId(), ResourceRoleName.all());
            return database;
        }
        Permission requiredPermission = this.securityManager
                .getPermissionByActions(database.getDataSource(), Collections.singletonList("read"));
        if (this.securityManager.isPermitted(requiredPermission)) {
            return database;
        }
        throw new NotFoundException(ResourceType.ODC_DATABASE, "id", id);
    }

    @SkipAuthorize("internal usage")
    public Database detailSkipPermissionCheck(@NonNull Long id) {
        return entityToModel(databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id)), true);
    }

    /**
     * This method provides database details for a task. Permission checks are skipped for internal use
     * only. The returned datasource includes decrypted attributes and password.
     */
    @SkipAuthorize("internal usage")
    public Database innerDetailForTask(@NonNull Long id) {
        return innerDetailForTask(databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id)));
    }

    @SkipAuthorize("odc internal usage")
    public Database getBasicSkipPermissionCheck(Long id) {
        return databaseMapper.entityToModel(databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id)));
    }

    @SkipAuthorize("odc internal usage")
    public List<Database> listBasicSkipPermissionCheckByIds(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        Set<Long> finalDatabaseIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<DatabaseEntity> dbs = databaseRepository.findByIdIn(finalDatabaseIds);
        Verify.equals(finalDatabaseIds.size(), dbs.size(), "Databases");
        return dbs.stream().map(databaseMapper::entityToModel).collect(Collectors.toList());
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public ConnectionConfig findDataSourceForConnectById(@NonNull Long id) {
        DatabaseEntity database = databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id));
        return connectionService.getForConnectionSkipPermissionCheck(database.getConnectionId());
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public ConnectionConfig findDataSourceForTaskById(@NonNull Long id) {
        DatabaseEntity database = databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id));
        return connectionService.getDecryptedConfig(database.getConnectionId());
    }

    @PreAuthenticate(actions = "read", resourceType = "ODC_CONNECTION", indexOfIdParam = 0)
    public Page<Database> listDatabasesByDataSource(@NonNull Long id, String name, Boolean existed,
            Boolean belongsToProject, @NonNull Pageable pageable) {
        Specification<DatabaseEntity> specs = DatabaseSpecs
                .connectionIdEquals(id)
                .and(DatabaseSpecs.existedEquals(existed))
                .and(DatabaseSpecs.projectIdNotNull(belongsToProject))
                .and(DatabaseSpecs.nameLike(name));
        Page<DatabaseEntity> entities = databaseRepository.findAll(specs, pageable);
        Page<Database> databases = entitiesToModels(entities, false);
        horizontalDataPermissionValidator.checkCurrentOrganization(databases.getContent());
        return databases;
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public Page<Database> list(@NonNull QueryDatabaseParams params, @NotNull Pageable pageable) {
        if (Objects.nonNull(params.getDataSourceId())
                && authenticationFacade.currentUser().getOrganizationType() == OrganizationType.INDIVIDUAL) {
            try {
                internalSyncDataSourceSchemas(params.getDataSourceId());
            } catch (Exception ex) {
                log.warn("sync data sources in individual space failed when listing databases, errorMessage={}",
                        ex.getLocalizedMessage());
            }
            params.setContainsUnassigned(true);
        }
        Specification<DatabaseEntity> specs = DatabaseSpecs
                .environmentIdEquals(params.getEnvironmentId())
                .and(DatabaseSpecs.typeIn(params.getTypes()))
                .and(DatabaseSpecs.connectTypeIn(params.getConnectTypes()))
                .and(DatabaseSpecs.existedEquals(params.getExisted()))
                .and(DatabaseSpecs.organizationIdEquals(authenticationFacade.currentOrganizationId()));
        Set<Long> joinedProjectIds =
                projectService
                        .list(QueryProjectParams.builder().build(), Pageable.unpaged())
                        .getContent().stream()
                        .filter(Objects::nonNull).map(Project::getId).collect(Collectors.toSet());
        /**
         * not joined any projects and does not show unassigned databases
         */
        if (joinedProjectIds.isEmpty()
                && (Objects.isNull(params.getContainsUnassigned()) || !params.getContainsUnassigned())) {
            return Page.empty();
        }

        if (Objects.isNull(params.getProjectId())) {
            Specification<DatabaseEntity> projectSpecs = DatabaseSpecs.projectIdIn(joinedProjectIds);
            if (Objects.nonNull(params.getContainsUnassigned()) && params.getContainsUnassigned()) {
                projectSpecs = projectSpecs.or(DatabaseSpecs.projectIdIsNull());
            }
            specs = specs.and(projectSpecs);
        } else {
            if (!joinedProjectIds.contains(params.getProjectId())) {
                throw new AccessDeniedException();
            }
            specs = specs.and(DatabaseSpecs.projectIdEquals(params.getProjectId()));
        }

        InnerQueryConnectionParams innerQueryConnectionParams = InnerQueryConnectionParams
                .builder()
                .dataSourceName(params.getDataSourceName())
                .tenantName(params.getTenantName())
                .clusterName(params.getClusterName())
                .build();
        Set<Long> orDataSourceIds = connectionService.innerGetIdsIfAnyOfCondition(innerQueryConnectionParams);
        if (Objects.nonNull(params.getDataSourceId())) {
            specs = specs.and(DatabaseSpecs.connectionIdEquals(params.getDataSourceId()));
        }
        if (CollectionUtils.isNotEmpty(orDataSourceIds)) {
            specs = specs.and(
                    DatabaseSpecs.nameLikeWithoutCase(params.getSchemaName())
                            .or(DatabaseSpecs.connectionIdIn(orDataSourceIds)));
        } else {
            specs = specs.and(DatabaseSpecs.nameLikeWithoutCase(params.getSchemaName()));
        }
        Page<DatabaseEntity> entities = databaseRepository.findAll(specs, pageable);
        return entitiesToModels(entities,
                Objects.nonNull(params.getIncludesPermittedAction()) && params.getIncludesPermittedAction());
    }

    @SkipAuthorize("internal authenticated")
    public List<ConnectionConfig> statsConnectionConfig() {
        QueryDatabaseParams params = QueryDatabaseParams.builder().build();
        if (authenticationFacade.currentUser().getOrganizationType() == OrganizationType.INDIVIDUAL) {
            return connectionService.listByOrganizationId(authenticationFacade.currentOrganizationId());
        }
        Page<Database> databases = list(params, Pageable.unpaged());
        if (CollectionUtils.isEmpty(databases.getContent())) {
            return Collections.emptyList();
        }
        return databases.stream().filter(database -> Objects.nonNull(database.getDataSource()))
                .map(Database::getDataSource)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(ConnectionConfig::getName))),
                        ArrayList::new));
    }

    @SkipAuthorize("internal authenticated")
    public Database create(@NonNull CreateDatabaseReq req) {
        ConnectionConfig connection = connectionService.getForConnectionSkipPermissionCheck(req.getDataSourceId());
        if ((connection.getProjectId() != null && !connection.getProjectId().equals(req.getProjectId()))
                || (Objects.nonNull(req.getProjectId())
                        && !projectPermissionValidator.hasProjectRole(req.getProjectId(),
                                Arrays.asList(ResourceRoleName.OWNER, ResourceRoleName.DBA)))
                || !connectionService.checkPermission(req.getDataSourceId(), Collections.singletonList("update"))) {
            throw new AccessDeniedException();
        }
        DataSource dataSource = new OBConsoleDataSourceFactory(connection, true, false, false).getDataSource();
        try (Connection conn = dataSource.getConnection()) {
            createDatabase(req, conn, connection);
            DBDatabase dbDatabase = dbSchemaService.detail(connection.getDialectType(), conn, req.getName());
            DatabaseEntity database = new DatabaseEntity();
            database.setDatabaseId(dbDatabase.getId());
            database.setExisted(Boolean.TRUE);
            database.setName(dbDatabase.getName());
            database.setCharsetName(dbDatabase.getCharset());
            database.setCollationName(dbDatabase.getCollation());
            database.setConnectionId(req.getDataSourceId());
            database.setProjectId(req.getProjectId());
            database.setEnvironmentId(connection.getEnvironmentId());
            database.setSyncStatus(DatabaseSyncStatus.SUCCEEDED);
            database.setOrganizationId(authenticationFacade.currentOrganizationId());
            database.setLastSyncTime(new Date(System.currentTimeMillis()));
            database.setObjectSyncStatus(DBObjectSyncStatus.INITIALIZED);
            database.setConnectType(connection.getType());
            database.setType(DatabaseType.PHYSICAL);
            DatabaseEntity saved = databaseRepository.saveAndFlush(database);
            List<UserResourceRole> userResourceRoles = buildUserResourceRoles(Collections.singleton(saved.getId()),
                    req.getOwnerIds());
            resourceRoleService.saveAll(userResourceRoles);
            return entityToModel(saved, false);
        } catch (Exception ex) {
            throw new BadRequestException(SqlExecuteResult.getTrackMessage(ex));
        } finally {
            if (dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource", e);
                }
            }
        }
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listDatabaseIdsByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectId(projectId).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listExistDatabaseIdsByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectIdAndExisted(projectId, true).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listDatabaseIdsByConnectionIds(@NotEmpty Collection<Long> connectionIds) {
        return databaseRepository.findByConnectionIdIn(connectionIds).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesByIds(@NotEmpty Collection<Long> ids) {
        return databaseRepository.findByIdIn(ids).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesDetailsByIds(@NotEmpty Collection<Long> ids) {
        Specification<DatabaseEntity> specs = DatabaseSpecs.idIn(ids);
        return entitiesToModels(databaseRepository.findAll(specs, Pageable.unpaged()), true).getContent();
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesByConnectionIds(@NotEmpty Collection<Long> connectionIds) {
        return databaseRepository.findByConnectionIdIn(connectionIds).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listExistDatabasesByConnectionId(@NotNull Long connectionId) {
        return databaseRepository.findByConnectionIdAndExisted(connectionId, true).stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listExistDatabasesByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectIdAndExisted(projectId, true).stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listExistAndNotPendingDatabasesByProjectIdIn(@NonNull Collection<Long> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptySet();
        }
        return databaseRepository
                .findByProjectIdInAndExistedAndObjectSyncStatusNot(projectIds, true, DBObjectSyncStatus.PENDING)
                .stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listExistAndNotPendingDatabasesByOrganizationId(@NonNull Long organizationId) {
        return databaseRepository
                .findByOrganizationIdAndExistedAndObjectSyncStatusNot(organizationId, true, DBObjectSyncStatus.PENDING)
                .stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listDatabaseByNames(@NotEmpty Collection<String> names) {
        return databaseRepository.findByNameIn(names).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toSet());
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public boolean transfer(@NonNull @Valid TransferDatabasesReq req) {
        List<DatabaseEntity> entities = databaseRepository.findAllById(req.getDatabaseIds());
        if (CollectionUtils.isEmpty(entities)) {
            return false;
        }
        checkTransferable(entities, req);
        Set<Long> databaseIds = entities.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        if (StringUtils.isNotBlank(req.getDatabaseRemark())) {
            checkIfCanUpsertDatabaseRemark(req.getProjectId());
            databaseRepository.setDatabaseRemarkByIdIn(databaseIds, req.getDatabaseRemark());
        }
        databaseRepository.setProjectIdByIdIn(req.getProjectId(), databaseIds);
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        List<UserResourceRole> userResourceRoles = buildUserResourceRoles(databaseIds, req.getOwnerIds());
        resourceRoleService.saveAll(userResourceRoles);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public boolean deleteDatabases(@NonNull DeleteDatabasesReq req) {
        if (CollectionUtils.isEmpty(req.getDatabaseIds())) {
            return true;
        }
        List<DatabaseEntity> saved = databaseRepository.findByIdIn(req.getDatabaseIds()).stream()
                .filter(database -> !database.getExisted())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(saved)) {
            return false;
        }
        saved.forEach(database -> checkPermission(database.getProjectId(), database.getConnectionId()));
        Set<Long> databaseIds = saved.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(req.getDatabaseIds());
        dbObjectRepository.deleteByDatabaseIdIn(req.getDatabaseIds());
        databaseRepository.deleteAll(saved);
        return true;
    }

    @PreAuthenticate(actions = "update", resourceType = "ODC_CONNECTION", indexOfIdParam = 0)
    public Boolean syncDataSourceSchemas(@NonNull Long dataSourceId) throws InterruptedException {
        Boolean res = internalSyncDataSourceSchemas(dataSourceId);
        if (res) {
            try {
                refreshExpiredPendingDBObjectStatus();
                dbSchemaSyncTaskManager
                        .submitTaskByDataSource(connectionService.getBasicWithoutPermissionCheck(dataSourceId));
            } catch (Exception e) {
                log.warn("Failed to submit sync database schema task for datasource id={}", dataSourceId, e);
            }
        }
        return res;
    }

    @SkipAuthorize("internal usage")
    public Boolean internalSyncDataSourceSchemas(@NonNull Long dataSourceId) throws InterruptedException {
        Lock lock = jdbcLockRegistry.obtain(connectionService.getUpdateDsSchemaLockKey(dataSourceId));
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            throw new ConflictException(ErrorCodes.ResourceSynchronizing,
                    new Object[] {ResourceType.ODC_DATABASE.getLocalizedMessage()}, "Can not acquire jdbc lock");
        }
        ConnectionConfig connection;
        Optional<Organization> organizationOpt = Optional.empty();
        try {
            connection = connectionService.getForConnectionSkipPermissionCheck(dataSourceId);
            if (connection.getType().isFileSystem()) {
                return true;
            }
            horizontalDataPermissionValidator.checkCurrentOrganization(connection);
            organizationOpt = organizationService.get(connection.getOrganizationId());
            Organization organization =
                    organizationOpt.orElseThrow(() -> new UnexpectedException("Organization not found"));
            if (organization.getType() == OrganizationType.INDIVIDUAL) {
                syncIndividualDataSources(connection);
            } else {
                syncTeamDataSources(connection);
            }
            connectionSyncHistoryService.upsert(connection.getId(), ConnectionSyncResult.SUCCESS,
                    connection.getOrganizationId(), null, null);
            return true;
        } catch (Exception ex) {
            handleSyncException(ex, dataSourceId, organizationOpt);
            return false;
        } finally {
            lock.unlock();
        }
    }

    @SkipAuthorize("odc internal usage")
    public int updateEnvironmentIdByConnectionId(@NotNull Long environmentId, @NotNull Long connectionId) {
        return databaseRepository.setEnvironmentIdByConnectionId(environmentId, connectionId);
    }

    private void syncTeamDataSources(ConnectionConfig connection)
            throws ExecutionException, InterruptedException, TimeoutException {
        Long currentProjectId = connection.getProjectId();
        boolean blockExcludeSchemas = dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject() && !bastionEnabled;
        List<String> excludeSchemas = dbSchemaSyncProperties.getExcludeSchemas(connection.getDialectType());
        DataSource teamDataSource = getDataSourceFactory(connection).getDataSource();
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        Future<List<DatabaseEntity>> future = executorService.submit(() -> {
            try (Connection conn = teamDataSource.getConnection()) {
                return dbSchemaService.listDatabases(connection.getDialectType(), conn).stream().map(database -> {
                    DatabaseEntity entity = new DatabaseEntity();
                    entity.setDatabaseId(com.oceanbase.odc.common.util.StringUtils.uuid());
                    entity.setConnectType(connection.getType());
                    entity.setExisted(Boolean.TRUE);
                    entity.setName(database.getName());
                    entity.setCharsetName(database.getCharset());
                    entity.setCollationName(database.getCollation());
                    entity.setTableCount(0L);
                    entity.setOrganizationId(connection.getOrganizationId());
                    entity.setEnvironmentId(connection.getEnvironmentId());
                    entity.setConnectionId(connection.getId());
                    entity.setSyncStatus(DatabaseSyncStatus.SUCCEEDED);
                    entity.setProjectId(currentProjectId);
                    entity.setObjectSyncStatus(DBObjectSyncStatus.INITIALIZED);
                    if (blockExcludeSchemas && excludeSchemas.contains(database.getName())) {
                        entity.setProjectId(null);
                    }
                    entity.setLastSyncTime(new Date(System.currentTimeMillis()));
                    return entity;
                }).collect(Collectors.toList());
            }
        });
        try {
            List<DatabaseEntity> latestDatabases = future.get(10, TimeUnit.SECONDS);
            Map<String, List<DatabaseEntity>> latestDatabaseName2Database =
                    latestDatabases.stream().filter(Objects::nonNull)
                            .collect(Collectors.groupingBy(DatabaseEntity::getName));
            List<DatabaseEntity> existedDatabasesInDb =
                    databaseRepository.findByConnectionId(connection.getId()).stream()
                            .filter(DatabaseEntity::getExisted).collect(Collectors.toList());
            Map<String, List<DatabaseEntity>> existedDatabaseName2Database =
                    existedDatabasesInDb.stream().collect(Collectors.groupingBy(DatabaseEntity::getName));

            Set<String> existedDatabaseNames = existedDatabaseName2Database.keySet();
            Set<String> latestDatabaseNames = latestDatabaseName2Database.keySet();
            List<Object[]> toAdd = latestDatabases.stream()
                    .filter(database -> !existedDatabaseNames.contains(database.getName()))
                    .map(database -> new Object[] {
                            database.getDatabaseId(),
                            database.getOrganizationId(),
                            database.getName(),
                            database.getProjectId(),
                            database.getConnectionId(),
                            database.getEnvironmentId(),
                            database.getSyncStatus().name(),
                            database.getCharsetName(),
                            database.getCollationName(),
                            database.getTableCount(),
                            database.getExisted(),
                            database.getObjectSyncStatus().name(),
                            database.getConnectType().name(),
                            database.getLastSyncTime()
                    }).collect(Collectors.toList());

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            if (CollectionUtils.isNotEmpty(toAdd)) {
                jdbcTemplate.batchUpdate(
                        "insert into connect_database(database_id, organization_id, name, project_id, connection_id, environment_id, sync_status, charset_name, collation_name, table_count, is_existed, object_sync_status, connect_type, last_sync_time) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        toAdd);
            }
            List<Object[]> toDelete = existedDatabasesInDb.stream()
                    .filter(database -> !latestDatabaseNames.contains(database.getName()))
                    .map(database -> new Object[] {getProjectId(database, currentProjectId, excludeSchemas),
                            new Date(System.currentTimeMillis()), database.getId()})
                    .collect(Collectors.toList());
            /**
             * just set existed to false if the database has been dropped instead of deleting it directly
             */
            if (!CollectionUtils.isEmpty(toDelete)) {
                String deleteSql =
                        "update connect_database set is_existed = 0, project_id=?, last_sync_time=? where id = ?";
                jdbcTemplate.batchUpdate(deleteSql, toDelete);
            }
            List<Object[]> toUpdate = existedDatabasesInDb.stream()
                    .filter(database -> latestDatabaseNames.contains(database.getName()))
                    .map(database -> {
                        DatabaseEntity latest = latestDatabaseName2Database.get(database.getName()).get(0);
                        return new Object[] {latest.getTableCount(), latest.getCollationName(), latest.getCharsetName(),
                                getProjectId(database, currentProjectId, excludeSchemas), latest.getLastSyncTime(),
                                database.getId()};
                    })
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(toUpdate)) {
                String update =
                        "update connect_database set table_count=?, collation_name=?, charset_name=?, project_id=?, last_sync_time=? where id = ?";
                jdbcTemplate.batchUpdate(update, toUpdate);
            }
            connectionSyncHistoryService.upsert(connection.getId(), ConnectionSyncResult.SUCCESS,
                    connection.getOrganizationId(), null, null);
        } finally {
            try {
                executorService.shutdownNow();
            } catch (Exception e) {
                // eat the exception
            }
            if (teamDataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) teamDataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource, errorMessgae={}", e.getMessage());
                }
            }
        }
    }

    private OBConsoleDataSourceFactory getDataSourceFactory(ConnectionConfig connection) {
        OBConsoleDataSourceFactory obConsoleDataSourceFactory =
                new OBConsoleDataSourceFactory(connection, true, false, false);
        LocalEventPublisher localEventPublisher = new LocalEventPublisher();
        localEventPublisher.addEventListener(new GetConnectionFailedEventListener());
        obConsoleDataSourceFactory.setEventPublisher(localEventPublisher);
        return obConsoleDataSourceFactory;
    }

    private Long getProjectId(DatabaseEntity database, Long currentProjectId, List<String> blockedDatabaseNames) {
        Long projectId;
        if (currentProjectId != null) {
            projectId = currentProjectId;
            if (dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject()
                    && blockedDatabaseNames.contains(database.getName()) && !bastionEnabled) {
                projectId = database.getProjectId();
            }
        } else {
            projectId = database.getProjectId();
        }
        return projectId;
    }

    private void syncIndividualDataSources(ConnectionConfig connection)
            throws ExecutionException, InterruptedException, TimeoutException {
        DataSource individualDataSource = getDataSourceFactory(connection).getDataSource();
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        Future<Set<String>> future = executorService.submit(() -> {
            try (Connection conn = individualDataSource.getConnection()) {
                return dbSchemaService.showDatabases(connection.getDialectType(), conn);
            }
        });
        try {
            Set<String> latestDatabaseNames = future.get(10, TimeUnit.SECONDS);
            List<DatabaseEntity> existedDatabasesInDb = databaseRepository.findByConnectionId(connection.getId())
                    .stream().filter(DatabaseEntity::getExisted).collect(Collectors.toList());
            Map<String, List<DatabaseEntity>> existedDatabaseName2Database =
                    existedDatabasesInDb.stream().collect(Collectors.groupingBy(DatabaseEntity::getName));
            Set<String> existedDatabaseNames = existedDatabaseName2Database.keySet();

            List<Object[]> toAdd = latestDatabaseNames.stream()
                    .filter(latestDatabaseName -> !existedDatabaseNames.contains(latestDatabaseName))
                    .map(latestDatabaseName -> new Object[] {
                            com.oceanbase.odc.common.util.StringUtils.uuid(),
                            connection.getOrganizationId(),
                            latestDatabaseName,
                            connection.getId(),
                            connection.getEnvironmentId(),
                            DatabaseSyncStatus.SUCCEEDED.name(),
                            DBObjectSyncStatus.INITIALIZED.name(),
                            connection.getType().name(),
                            new Date(System.currentTimeMillis())
                    })
                    .collect(Collectors.toList());

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            if (CollectionUtils.isNotEmpty(toAdd)) {
                jdbcTemplate.batchUpdate(
                        "insert into connect_database(database_id, organization_id, name, connection_id, environment_id, sync_status, object_sync_status, connect_type, last_sync_time) values(?,?,?,?,?,?,?,?,?)",
                        toAdd);
            }

            List<Object[]> toDelete =
                    existedDatabasesInDb.stream()
                            .filter(database -> !latestDatabaseNames.contains(database.getName()))
                            .map(database -> new Object[] {database.getId()})
                            .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(toDelete)) {
                jdbcTemplate.batchUpdate("delete from connect_database where id = ?", toDelete);
            }
            connectionSyncHistoryService.upsert(connection.getId(), ConnectionSyncResult.SUCCESS,
                    connection.getOrganizationId(), null, null);
        } finally {
            try {
                executorService.shutdownNow();
            } catch (Exception e) {
                // eat the exception
            }
            if (individualDataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) individualDataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource, errorMessgae={}", e.getMessage());
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal usage")
    public int deleteByDataSourceIds(@NonNull Set<Long> dataSourceId) {
        List<Long> databaseIds = databaseRepository.findByConnectionIdIn(dataSourceId).stream()
                .map(DatabaseEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(databaseIds)) {
            return 0;
        }
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(databaseIds);
        dbObjectRepository.deleteByDatabaseIdIn(databaseIds);
        return databaseRepository.deleteByConnectionIds(dataSourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal usage")
    public int deleteByDataSourceId(@NonNull Long dataSourceId) {
        List<Long> databaseIds = databaseRepository.findByConnectionId(dataSourceId).stream()
                .map(DatabaseEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(databaseIds)) {
            return 0;
        }
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(databaseIds);
        dbObjectRepository.deleteByDatabaseIdIn(databaseIds);
        return databaseRepository.deleteByConnectionId(dataSourceId);
    }

    @SkipAuthorize("internal usage")
    public List<AuthorizedDatabase> getAllAuthorizedDatabases(@NonNull Long dataSourceId) {
        List<Database> databases = listDatabasesByConnectionIds(Collections.singleton(dataSourceId));
        Map<Long, Set<DatabasePermissionType>> id2Types = permissionHelper
                .getDBPermissions(databases.stream().map(Database::getId).collect(Collectors.toList()));
        return databases.stream().map(d -> new AuthorizedDatabase(d.getId(), d.getName(), id2Types.get(d.getId())))
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal authorized")
    public Page<DatabaseUser> listUserForOsc(Long dataSourceId) {
        ConnectionConfig config = connectionService.getForConnectionSkipPermissionCheck(dataSourceId);
        horizontalDataPermissionValidator.checkCurrentOrganization(config);
        DefaultConnectSessionFactory factory = new DefaultConnectSessionFactory(config);
        ConnectionSession connSession = factory.generateSession();
        try {
            OscDBAccessor dbSchemaAccessor = new OscDBAccessorFactory().generate(connSession);
            List<DBUser> dbUsers = dbSchemaAccessor.listUsers(null);
            Set<String> whiteUsers = OscDBUserUtil.getLockUserWhiteList(config);

            return new PageImpl<>(dbUsers.stream()
                    .filter(u -> !whiteUsers.contains(u.getName()))
                    .map(d -> DatabaseUser.builder().name(d.getNameWithHost()).build())
                    .collect(Collectors.toList()));
        } finally {
            connSession.expire();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthenticate(hasAnyResourceRole = {"OWNER", "DBA"}, actions = {"OWNER", "DBA"}, resourceType = "ODC_PROJECT",
            indexOfIdParam = 0)
    public boolean modifyDatabasesOwners(@NotNull Long projectId, @NotNull @Valid ModifyDatabaseOwnerReq req) {
        databaseRepository.findByIdIn(req.getDatabaseIds()).forEach(database -> {
            if (!projectId.equals(database.getProjectId())) {
                throw new AccessDeniedException();
            }
        });
        Set<Long> memberIds =
                resourceRoleService.listByResourceTypeAndResourceId(ResourceType.ODC_PROJECT, projectId).stream()
                        .map(UserResourceRole::getUserId).collect(Collectors.toSet());
        if (!memberIds.containsAll(req.getOwnerIds())) {
            throw new AccessDeniedException();
        }
        resourceRoleService.deleteByResourceTypeAndIdIn(ResourceType.ODC_DATABASE, req.getDatabaseIds());
        List<UserResourceRole> userResourceRoles = new ArrayList<>();
        req.getDatabaseIds().forEach(databaseId -> {
            userResourceRoles.addAll(req.getOwnerIds().stream().map(userId -> {
                UserResourceRole userResourceRole = new UserResourceRole();
                userResourceRole.setUserId(userId);
                userResourceRole.setResourceId(databaseId);
                userResourceRole.setResourceType(ResourceType.ODC_DATABASE);
                userResourceRole.setResourceRole(ResourceRoleName.OWNER);
                return userResourceRole;
            }).collect(Collectors.toList()));
        });
        resourceRoleService.saveAll(userResourceRoles);
        return true;
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void updateObjectSyncStatus(@NotNull Collection<Long> databaseIds, @NotNull DBObjectSyncStatus status) {
        if (CollectionUtils.isEmpty(databaseIds)) {
            return;
        }
        databaseRepository.setObjectSyncStatusByIdIn(databaseIds, status);
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void updateObjectLastSyncTimeAndStatus(@NotNull Long databaseId,
            @NotNull DBObjectSyncStatus status) {
        databaseRepository.setObjectLastSyncTimeAndStatusById(databaseId, new Date(), status);
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void refreshExpiredPendingDBObjectStatus() {
        Date syncDate = new Date(System.currentTimeMillis() - this.globalSearchProperties.getMaxPendingMillis());
        int affectRows =
                this.databaseRepository.setObjectSyncStatusByObjectSyncStatusAndObjectLastSyncTimeIsNullOrBefore(
                        DBObjectSyncStatus.INITIALIZED, DBObjectSyncStatus.PENDING, syncDate);
        log.info("Refresh outdated pending objects status, syncDate={}, affectRows={}", syncDate, affectRows);
    }

    @SkipAuthorize("internal usage")
    public List<Database> listSkipPermissionCheck(@NonNull Collection<Long> ids, boolean includesPermittedAction) {
        Set<Long> databaseIds =
                ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (CollectionUtils.isEmpty(databaseIds)) {
            return Collections.emptyList();
        }
        final Map<Long, DatabaseEntity> dbId2Database = databaseRepository.findByIdIn(databaseIds).stream().collect(
                Collectors.toMap(DatabaseEntity::getId, Function.identity()));

        // Ensure that the databases tracked down remain in relative order according to databaseIds
        List<DatabaseEntity> dbs =
                databaseIds.stream().map(dbId2Database::get).filter(Objects::nonNull).collect(Collectors.toList());
        return entitiesToModels(new PageImpl<>(dbs),
                includesPermittedAction).getContent();
    }

    @SkipAuthorize("odc internal usage")
    public boolean recordDatabaseAccessHistory(@NonNull Collection<Long> databaseIds) {
        databaseIds.removeIf(Objects::isNull);
        if (CollectionUtils.isEmpty(databaseIds)) {
            return true;
        }
        final Date now = new Date();
        final long userId = authenticationFacade.currentUserId();

        Set<Long> joinedProjectIds = projectService.getMemberProjectIds(userId);
        if (CollectionUtils.isEmpty(joinedProjectIds)) {
            log.warn("No member project found for user, can't record database history, dbIds={}",
                    JsonUtils.toJson(databaseIds));
            return false;
        }
        List<Database> dbs = entitiesToModels(
                new PageImpl<>(databaseRepository.findByIdInAndProjectIdIn(databaseIds, joinedProjectIds)),
                false).getContent();

        Verify.equals(databaseIds.size(), dbs.size(), "Database");

        final List<DatabaseAccessHistoryEntity> toUpsertHistories =
                dbs.stream().map(e -> {
                    DatabaseAccessHistoryEntity databaseAccessHistoryEntity = new DatabaseAccessHistoryEntity();
                    databaseAccessHistoryEntity.setLastAccessTime(now);
                    databaseAccessHistoryEntity.setDatabaseId(e.getId());
                    // If dataSource is null, may logical database
                    if (e.getDataSource() != null) {
                        databaseAccessHistoryEntity.setConnectionId(e.getDataSource().getId());
                    }
                    databaseAccessHistoryEntity.setUserId(userId);
                    return databaseAccessHistoryEntity;
                }).collect(Collectors.toList());

        int affectRows = databaseAccessHistoryRepository.upsert(toUpsertHistories);
        log.info("Record database access histories, expected total is {}, actual is {}",
                dbs.size(), affectRows);
        return Objects.equals(affectRows, dbs.size());
    }

    @SkipAuthorize("odc internal usage")
    public List<Database> listDatabaseAccessHistory(@NonNull @Valid DBAccessHistoryReq dbAccessHistoryReq) {
        final long userId = authenticationFacade.currentUserId();
        if (dbAccessHistoryReq.getHistoryCount() == null) {
            return Collections.emptyList();
        }
        Pageable page = PageRequest.of(0, dbAccessHistoryReq.getHistoryCount())
                .withSort(Sort.by(DatabaseAccessHistoryEntity.LAST_ACCESS_TIME_NAME).descending());
        List<DatabaseAccessHistoryEntity> dbHistoryEntities =
                databaseAccessHistoryRepository.findByUserId(userId, page).getContent();
        Set<Long> dbIds =
                dbHistoryEntities.stream().map(DatabaseAccessHistoryEntity::getDatabaseId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Database> databases = listSkipPermissionCheck(dbIds, true);
        if (CollectionUtils.isNotEmpty(databases)) {
            Map<Long, Set<ResourceRoleName>> projectId2ResourceRoleNames =
                    projectService.getProjectId2ResourceRoleNames();
            databases.forEach(d -> {
                Project project = d.getProject();
                if (project != null) {
                    project.setCurrentUserResourceRoles(
                            projectId2ResourceRoleNames.getOrDefault(project.getId(), Collections.emptySet()));
                }
            });
        }
        return databases;
    }

    @SkipAuthorize("internal authorized")
    @Transactional(rollbackFor = Exception.class)
    public boolean modifyDatabaseRemark(@NotEmpty Collection<Long> databaseIds,
            @NotNull @Size(min = 0, max = 100) String remark) {
        Set<Long> ids = new HashSet<>(databaseIds);
        List<Database> databases = listDatabasesByIds(ids);
        Verify.equals(ids.size(), databases.size(), "Missing databases may exist");

        Set<Long> needCheckProjectIds =
                databases.stream().filter(d -> d.getProject() != null && d.getProject().getId() != null)
                        .map(d -> d.getProject().getId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(needCheckProjectIds)) {
            return false;
        }
        checkIfCanUpsertDatabaseRemark(needCheckProjectIds);

        int affectRows = databaseRepository.setDatabaseRemarkByIdIn(databaseIds, remark);
        return Objects.equals(affectRows, databases.size());
    }

    private void checkPermission(Long projectId, Long dataSourceId) {
        if (Objects.isNull(projectId) && Objects.isNull(dataSourceId)) {
            throw new AccessDeniedException("invalid projectId or dataSourceId");
        }
        boolean isProjectMember = false;
        if (Objects.nonNull(projectId)) {
            isProjectMember = projectPermissionValidator.hasProjectRole(projectId, ResourceRoleName.all());
        }
        boolean canUpdateDataSource = false;
        if (Objects.nonNull(dataSourceId)) {
            canUpdateDataSource = connectionService.checkPermission(dataSourceId, Arrays.asList("update"));
        }
        if (!isProjectMember && !canUpdateDataSource) {
            throw new AccessDeniedException("invalid projectId or dataSourceId");
        }
    }

    private void checkIfCanUpsertDatabaseRemark(@NonNull Long projectId) {
        projectPermissionValidator.checkProjectRole(projectId,
                Arrays.asList(ResourceRoleName.OWNER, ResourceRoleName.DBA));
    }

    private void checkIfCanUpsertDatabaseRemark(Collection<Long> projectIds) {
        if (CollectionUtils.isNotEmpty(projectIds)) {
            projectPermissionValidator.checkProjectRole(projectIds,
                    Arrays.asList(ResourceRoleName.OWNER, ResourceRoleName.DBA));
        }
    }

    private void checkTransferable(@NonNull Collection<DatabaseEntity> databases, @NonNull TransferDatabasesReq req) {
        if (CollectionUtils.isEmpty(databases)) {
            return;
        }
        if (Objects.nonNull(req.getProjectId())) {
            projectPermissionValidator.checkProjectRole(req.getProjectId(),
                    Arrays.asList(ResourceRoleName.DBA, ResourceRoleName.OWNER));
        }
        if (CollectionUtils.isNotEmpty(req.getOwnerIds())) {
            Set<Long> memberIds =
                    resourceRoleService.listByResourceTypeAndResourceId(ResourceType.ODC_PROJECT, req.getProjectId())
                            .stream()
                            .map(UserResourceRole::getUserId).collect(Collectors.toSet());
            PreConditions.validArgumentState(memberIds.containsAll(req.getOwnerIds()), ErrorCodes.AccessDenied, null,
                    "Invalid ownerIds");
        }
        List<Long> projectIds = databases.stream().map(DatabaseEntity::getProjectId).collect(Collectors.toList());
        List<Long> connectionIds = databases.stream().map(DatabaseEntity::getConnectionId).collect(Collectors.toList());
        projectPermissionValidator.checkProjectRole(projectIds,
                Arrays.asList(ResourceRoleName.DBA, ResourceRoleName.OWNER));
        PreConditions.validArgumentState(
                connectionService.checkPermission(connectionIds, Collections.singletonList("update")),
                ErrorCodes.AccessDenied, null, "Lack of update permission on current datasource");
        Map<Long, ConnectionConfig> id2Conn = connectionService.innerListByIds(connectionIds).stream()
                .collect(Collectors.toMap(ConnectionConfig::getId, c -> c, (c1, c2) -> c2));
        if (dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject() && !bastionEnabled) {
            connectionIds = databases.stream().filter(database -> {
                ConnectionConfig connection = id2Conn.get(database.getConnectionId());
                return connection != null && !dbSchemaSyncProperties.getExcludeSchemas(connection.getDialectType())
                        .contains(database.getName());
            }).map(DatabaseEntity::getConnectionId).collect(Collectors.toList());
        }
        connectionIds.forEach(c -> {
            ConnectionConfig connection = id2Conn.get(c);
            if (connection == null) {
                throw new NotFoundException(ResourceType.ODC_CONNECTION, "id", c);
            }
            PreConditions.validArgumentState(connection.getProjectId() == null, ErrorCodes.AccessDenied, null,
                    "Cannot transfer databases in datasource which is bound to project");
        });
    }

    private Page<Database> entitiesToModels(Page<DatabaseEntity> entities, boolean includesPermittedAction) {
        if (CollectionUtils.isEmpty(entities.getContent())) {
            return Page.empty();
        }
        Map<Long, Project> projectId2Project = projectService.mapByIdIn(entities.stream()
                .map(DatabaseEntity::getProjectId).collect(Collectors.toSet()));
        Map<Long, List<ConnectionConfig>> connectionId2Connections = connectionService.mapByIdIn(entities.stream()
                .map(DatabaseEntity::getConnectionId).collect(Collectors.toSet()));
        Map<Long, Set<DatabasePermissionType>> databaseId2PermittedActions = new HashMap<>();
        Map<Long, Environment> id2Environments = environmentService.list(
                QueryEnvironmentParam.builder().build()).stream()
                .collect(Collectors.toMap(Environment::getId, env -> env, (v1, v2) -> v2));
        Set<Long> databaseIds = entities.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        if (includesPermittedAction) {
            databaseId2PermittedActions = permissionHelper.getDBPermissions(databaseIds);
        }
        Map<Long, Set<DatabasePermissionType>> finalId2PermittedActions = databaseId2PermittedActions;
        Map<Long, List<UserResourceRole>> databaseId2UserResourceRole = new HashMap<>();
        Map<Long, User> userId2User = new HashMap<>();
        List<UserResourceRole> userResourceRoles =
                resourceRoleService.listByResourceTypeAndResourceIdIn(ResourceType.ODC_DATABASE, databaseIds);
        if (CollectionUtils.isNotEmpty(userResourceRoles)) {
            databaseId2UserResourceRole = userResourceRoles.stream()
                    .collect(Collectors.groupingBy(UserResourceRole::getResourceId, Collectors.toList()));
            userId2User = userService
                    .batchNullSafeGet(
                            userResourceRoles.stream().map(UserResourceRole::getUserId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(User::getId, v -> v, (v1, v2) -> v2));
        }
        Map<Long, List<UserResourceRole>> finalDatabaseId2UserResourceRole = databaseId2UserResourceRole;
        Map<Long, User> finalUserId2User = userId2User;
        return entities.map(entity -> {
            Database database = databaseMapper.entityToModel(entity);
            Project project = projectId2Project.get(entity.getProjectId());
            List<ConnectionConfig> connections =
                    connectionId2Connections.getOrDefault(entity.getConnectionId(), new ArrayList<>());
            database.setProject(project);
            database.setEnvironment(id2Environments.getOrDefault(entity.getEnvironmentId(), null));
            database.setDataSource(CollectionUtils.isEmpty(connections) ? null : connections.get(0));
            if (includesPermittedAction) {
                database.setAuthorizedPermissionTypes(finalId2PermittedActions.get(entity.getId()));
            }

            // Set the owner of the database
            List<UserResourceRole> resourceRoles = finalDatabaseId2UserResourceRole.get(entity.getId());
            if (CollectionUtils.isNotEmpty(resourceRoles)) {
                Set<Long> ownerIds =
                        resourceRoles.stream().map(UserResourceRole::getUserId).collect(Collectors.toSet());
                List<InnerUser> owners = ownerIds.stream().map(id -> {
                    User user = finalUserId2User.get(id);
                    InnerUser innerUser = new InnerUser();
                    innerUser.setId(user.getId());
                    innerUser.setName(user.getName());
                    innerUser.setAccountName(user.getAccountName());
                    return innerUser;
                }).collect(Collectors.toList());
                database.setOwners(owners);
            }
            return database;
        });
    }

    private Database innerDetailForTask(DatabaseEntity entity) {
        Database model = databaseMapper.entityToModel(entity);
        if (Objects.nonNull(entity.getProjectId())) {
            model.setProject(projectService.detail(entity.getProjectId()));
        }
        // for logical database, the connection id may be null
        if (entity.getConnectionId() != null) {
            model.setDataSource(connectionService.getDecryptedConfig(entity.getConnectionId()));
        }
        model.setEnvironment(environmentService.detailSkipPermissionCheck(entity.getEnvironmentId()));
        return model;
    }


    private Database entityToModel(DatabaseEntity entity, boolean includesPermittedAction) {
        Database model = databaseMapper.entityToModel(entity);
        if (Objects.nonNull(entity.getProjectId())) {
            model.setProject(projectService.detail(entity.getProjectId()));
        }
        // for logical database, the connection id may be null
        if (entity.getConnectionId() != null) {
            model.setDataSource(connectionService.getForConnectionSkipPermissionCheck(entity.getConnectionId()));
        }
        model.setEnvironment(environmentService.detailSkipPermissionCheck(entity.getEnvironmentId()));
        if (includesPermittedAction) {
            model.setAuthorizedPermissionTypes(
                    permissionHelper.getDBPermissions(Collections.singleton(entity.getId())).get(entity.getId()));
        }
        return model;
    }

    private void createDatabase(CreateDatabaseReq req, Connection conn, ConnectionConfig connection) {
        DBDatabase db = new DBDatabase();
        db.setName(req.getName());
        db.setCharset(req.getCharsetName());
        db.setCollation(req.getCollationName());
        SchemaPluginUtil.getDatabaseExtension(connection.getDialectType()).create(conn, db, connection.getPassword());
    }

    private void deleteDatabaseRelatedPermissionByIds(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        List<Long> permissionIds = userDatabasePermissionRepository.findByDatabaseIdIn(ids).stream()
                .map(UserDatabasePermissionEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(permissionIds)) {
            permissionRepository.deleteByIds(permissionIds);
            userPermissionRepository.deleteByPermissionIds(permissionIds);
        }
        permissionIds = userTablePermissionRepository.findByDatabaseIdIn(ids).stream()
                .map(UserTablePermissionEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(permissionIds)) {
            permissionRepository.deleteByIds(permissionIds);
            userPermissionRepository.deleteByPermissionIds(permissionIds);
        }
        resourceRoleService.deleteByResourceTypeAndIdIn(ResourceType.ODC_DATABASE, ids);
    }

    private List<UserResourceRole> buildUserResourceRoles(Collection<Long> databaseIds, Collection<Long> ownerIds) {
        List<UserResourceRole> userResourceRoles = new ArrayList<>();
        if (CollectionUtils.isEmpty(databaseIds) || CollectionUtils.isEmpty(ownerIds)) {
            return userResourceRoles;
        }
        databaseIds.forEach(databaseId -> {
            userResourceRoles.addAll(ownerIds.stream().map(userId -> {
                UserResourceRole userResourceRole = new UserResourceRole();
                userResourceRole.setUserId(userId);
                userResourceRole.setResourceId(databaseId);
                userResourceRole.setResourceType(ResourceType.ODC_DATABASE);
                userResourceRole.setResourceRole(ResourceRoleName.OWNER);
                return userResourceRole;
            }).collect(Collectors.toList()));
        });
        return userResourceRoles;
    }

    private void handleSyncException(@NonNull Exception ex, @NonNull Long dataSourceId,
            @NonNull Optional<Organization> organizationOpt) {
        String errorMessage = ex.getMessage();
        log.warn("Sync database failed, dataSourceId={}, errorMessage={}", dataSourceId, errorMessage);
        if (!organizationOpt.isPresent()) {
            return;
        }
        Organization organization = organizationOpt.get();
        ConnectionSyncErrorReason failedReason = ConnectionSyncErrorReason.UNKNOWN;
        if (StringUtils.containsIgnoreCase(errorMessage, "cluster not exist")) {
            failedReason = ConnectionSyncErrorReason.CLUSTER_NOT_EXISTS;
            deleteDatabaseIfInstanceNotExists(dataSourceId, organization.getType());
        } else if (StringUtils.containsIgnoreCase(errorMessage, "No tenants found") || StringUtils
                .containsIgnoreCase(errorMessage, "tenant expected 1 but was")) {
            failedReason = ConnectionSyncErrorReason.TENANT_NOT_EXISTS;
            deleteDatabaseIfInstanceNotExists(dataSourceId, organization.getType());
        }
        connectionSyncHistoryService.upsert(dataSourceId, ConnectionSyncResult.FAILURE, organization.getId(),
                failedReason, errorMessage);
    }

    private void deleteDatabaseIfInstanceNotExists(Long connectionId, OrganizationType organizationType) {
        log.info(
                "Cluster or tenant not exist, set existed to false for all databases in this data source, data source id = {}",
                connectionId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String deleteSql;
        if (organizationType == OrganizationType.INDIVIDUAL) {
            deleteSql = "delete from connect_database where connection_id=?";
        } else {
            deleteSql = "update connect_database set is_existed = 0 where connection_id=?";
        }
        try {
            jdbcTemplate.update(deleteSql, connectionId);
        } catch (Exception ex) {
            log.warn("Failed to delete databases when cluster not exist, errorMessage={}",
                    ex.getLocalizedMessage());
        }

    }

    public <T> void assignDatabaseById(List<T> content, Function<T, Long> databaseIdProvider,
            BiConsumer<T, Database> databaseSetter) {
        List<Long> databaseIds = content.stream().map(databaseIdProvider).collect(
                Collectors.toList());
        List<DatabaseEntity> entities = databaseRepository.findByIdIn(databaseIds);
        List<Database> databases = entitiesToModels(new PageImpl<>(entities), false).getContent();
        Map<Long, Database> idDatabaseEntityMap = databases.stream().collect(
                Collectors.toMap(Database::getId, t -> t));
        content.forEach(c -> {
            Database database = idDatabaseEntityMap.get(databaseIdProvider.apply(c));
            databaseSetter.accept(c, database);
        });
    }
}
