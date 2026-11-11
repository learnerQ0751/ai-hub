package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.RagAnswerService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 生成与引用溯源入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=RAG，target_name=rag_answer，
 * 只做 RPM，调用轨迹写 call_logs（cost=0）；内部 chat 不再经过 Filter。</p>
 */
@RestController
public class RagAnswerController {

    private final RagAnswerService ragAnswerService;

    /**
     * 构造控制器。
     *
     * @param ragAnswerService 生成服务
     */
    public RagAnswerController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    /**
     * 检索并生成带引用的答案。
     *
     * @param reqDTO  生成参数
     * @param request HTTP 请求（认证与请求追踪上下文）
     * @return 答案、引用与用量
     */
    @PostMapping(path = "/v1/rag/answer", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagAnswerResDTO answer(@RequestBody(required = false) RagAnswerReqDTO reqDTO,
                                  HttpServletRequest request) {
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return ragAnswerService.answer(reqDTO, auth, requestId);
    }
}
