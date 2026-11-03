package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.PromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * `prompt_templates` 表 Mapper。
 */
@Mapper
public interface PromptTemplateMapper {

    /**
     * 插入版本。
     *
     * @param prompt 数据对象
     */
    void insert(PromptTemplateDO prompt);

    /**
     * 当前最大版本号。
     *
     * @param name 模板名
     * @return 最大版本；无记录时为 null
     */
    Integer selectMaxVersion(@Param("name") String name);

    /**
     * 查询最新发布版本。
     *
     * @param name 模板名
     * @return 发布版本；不存在为空
     */
    Optional<PromptTemplateDO> selectPublished(@Param("name") String name);

    /**
     * 按版本查询。
     *
     * @param name    模板名
     * @param version 版本
     * @return 版本记录；不存在为空
     */
    Optional<PromptTemplateDO> selectByVersion(@Param("name") String name,
                                               @Param("version") int version);

    /**
     * 查询全部版本（按版本倒序）。
     *
     * @param name 模板名
     * @return 版本列表
     */
    List<PromptTemplateDO> selectVersions(@Param("name") String name);

    /**
     * 查询全部已发布版本（多 name）。
     *
     * @return 发布版本列表
     */
    List<PromptTemplateDO> selectAllPublished();

    /**
     * 锁定同 name 的行（发布事务用）。
     *
     * @param name 模板名
     * @return 锁定行 id；name 不存在时为 null
     */
    Long lockByName(@Param("name") String name);

    /**
     * 把同 name 的 PUBLISHED 全部置为 ARCHIVED。
     *
     * @param name 模板名
     * @return 受影响行数
     */
    int archivePublished(@Param("name") String name);

    /**
     * 发布指定版本。
     *
     * @param name    模板名
     * @param version 版本
     * @return 受影响行数
     */
    int publishVersion(@Param("name") String name, @Param("version") int version);
}
