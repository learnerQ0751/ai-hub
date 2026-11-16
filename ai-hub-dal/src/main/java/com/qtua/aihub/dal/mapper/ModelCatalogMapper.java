package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.ModelAliasDO;
import com.qtua.aihub.dal.model.ModelDeploymentDO;
import com.qtua.aihub.dal.model.ModelCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 模型别名/部署实例 Mapper（REQ-4.1/4.4）。
 *
 * <p>目录读取走联表查询；管理端写入拆成别名、部署实例与两张关系表。</p>
 */
@Mapper
public interface ModelCatalogMapper {

    /**
     * 联表读取全部激活别名与部署。
     *
     * @return 目录行
     */
    List<ModelCatalogRow> selectCatalogRows();

    /**
     * 读取全部别名（含无部署的别名）。
     *
     * @return 别名列表
     */
    List<String> selectAliasNames();

    /**
     * 按别名查询全部部署（含停用，管理端用）。
     *
     * @param alias 别名
     * @return 目录行
     */
    List<ModelCatalogRow> selectRowsByAlias(@Param("alias") String alias);

    /**
     * 别名数量（启动种子判断）。
     *
     * @return 数量
     */
    int countAliases();

    /**
     * 按别名查询。
     *
     * @param alias 别名
     * @return 别名记录
     */
    Optional<ModelAliasDO> selectByAlias(@Param("alias") String alias);

    /**
     * 查询全部别名（含停用）。
     *
     * @return 别名记录
     */
    List<ModelAliasDO> selectAllAliases();

    /**
     * 插入别名。
     *
     * @param aliasDO 别名记录
     */
    void insertAlias(ModelAliasDO aliasDO);

    /**
     * 更新别名描述/降级链/状态。
     *
     * @param aliasDO 别名记录
     * @return 受影响行数
     */
    int updateAlias(ModelAliasDO aliasDO);

    /**
     * 删除别名。
     *
     * @param alias 别名
     * @return 受影响行数
     */
    int deleteAlias(@Param("alias") String alias);

    /**
     * 按 deployment_key 查询部署实例。
     *
     * @param deploymentKey 部署键
     * @return 部署记录
     */
    Optional<ModelDeploymentDO> selectDeploymentByKey(@Param("deploymentKey") String deploymentKey);

    /**
     * 插入部署实例。
     *
     * @param deploymentDO 部署记录
     */
    void insertDeployment(ModelDeploymentDO deploymentDO);

    /**
     * 更新部署实例（地址/密文/真实模型/状态）。
     *
     * @param deploymentDO 部署记录
     * @return 受影响行数
     */
    int updateDeployment(ModelDeploymentDO deploymentDO);

    /**
     * 删除部署实例。
     *
     * @param deploymentKey 部署键
     * @return 受影响行数
     */
    int deleteDeployment(@Param("deploymentKey") String deploymentKey);

    /**
     * 关联别名与部署（重复关联时更新权重/优先级）。
     *
     * @param aliasId      别名 ID
     * @param deploymentId 部署 ID
     * @param weight       权重
     * @param priority     优先级
     * @return 受影响行数
     */
    int insertAliasDeployment(@Param("aliasId") Long aliasId,
                              @Param("deploymentId") Long deploymentId,
                              @Param("weight") int weight,
                              @Param("priority") int priority);

    /**
     * 更新别名与部署的权重/优先级。
     *
     * @param aliasId      别名 ID
     * @param deploymentId 部署 ID
     * @param weight       权重
     * @param priority     优先级
     * @return 受影响行数
     */
    int updateAliasDeployment(@Param("aliasId") Long aliasId,
                              @Param("deploymentId") Long deploymentId,
                              @Param("weight") int weight,
                              @Param("priority") int priority);

    /**
     * 删除别名的全部部署关联。
     *
     * @param aliasId 别名 ID
     * @return 受影响行数
     */
    int deleteAliasDeployments(@Param("aliasId") Long aliasId);

    /**
     * 删除部署的全部别名关联。
     *
     * @param deploymentId 部署 ID
     * @return 受影响行数
     */
    int deleteDeploymentAliases(@Param("deploymentId") Long deploymentId);

    /**
     * 绑定 Key 到别名（幂等）。
     *
     * @param aliasId 别名 ID
     * @param keyHash Key 哈希
     * @return 受影响行数
     */
    int insertAliasKey(@Param("aliasId") Long aliasId, @Param("keyHash") String keyHash);

    /**
     * 查询别名绑定的 Key。
     *
     * @param aliasId 别名 ID
     * @return Key 哈希列表
     */
    List<String> selectAliasKeys(@Param("aliasId") Long aliasId);
}
