package xyz.eulix.platform.services.mgtboard.service;

import org.jboss.logging.Logger;
import xyz.eulix.platform.services.mgtboard.dto.*;
import xyz.eulix.platform.services.mgtboard.entity.QuestionnaireEntity;
import xyz.eulix.platform.services.mgtboard.entity.QuestionnaireFeedbackEntity;
import xyz.eulix.platform.services.mgtboard.repository.QaEntityRepository;
import xyz.eulix.platform.services.mgtboard.repository.QaFeedbackEntityRepository;
import xyz.eulix.platform.services.support.CommonUtils;
import xyz.eulix.platform.services.support.model.PageInfo;
import xyz.eulix.platform.services.support.model.PageListResult;
import xyz.eulix.platform.services.support.serialization.OperationUtils;
import xyz.eulix.platform.services.support.service.ServiceError;
import xyz.eulix.platform.services.support.service.ServiceOperationException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class QuestionnaireService {
    private static final Logger LOG = Logger.getLogger("app.log");

    @Inject
    QaEntityRepository qaEntityRepository;

    @Inject
    QaFeedbackEntityRepository qaFeedbackEntityRepository;

    @Inject
    OperationUtils operationUtils;

    /**
     * �����ʾ�
     *
     * @param qaReq �ʾ�
     * @return �ʾ�
     */
    @Transactional
    public QuestionnaireRes saveQuestionnaire(QuestionnaireReq qaReq) {
        // ��ѯ�ʾ��Ƿ����
        Optional<QuestionnaireEntity> qaEntityOp = qaEntityRepository.findBySurveyId(qaReq.getPayloadSurveyId());
        if (!qaEntityOp.isEmpty()) {
            LOG.warnv("payload survey already exist, payloadSurveyId:{0}", qaReq.getPayloadSurveyId());
            throw new ServiceOperationException(ServiceError.PAYLOAD_SURVEY_ALREADY_EXIST);
        }
        QuestionnaireEntity qaEntity = qaReqToEntity(qaReq);
        qaEntityRepository.persist(qaEntity);
        return qaEntityToRes(qaEntity);
    }

    /**
     * �����ʾ�
     *
     * @param qaId �ʾ�id
     * @param updateReq ��������
     * @return �ʾ�
     */
    @Transactional
    public QuestionnaireRes updateQuestionnaire(Long qaId, QuestionnaireUpdateReq updateReq) {
        QuestionnaireEntity qaEntity = qaEntityRepository.findById(qaId);
        if (qaEntity == null) {
            LOG.warnv("questionnaire does not exist, questionnaireId:{0}", qaId);
            throw new ServiceOperationException(ServiceError.QUESTIONNAIRE_NOT_EXIST);
        }
        qaEntityRepository.updateById(qaId, updateReq.getTitle(), updateReq.getStartAt(), updateReq.getEndAt());
        return QuestionnaireRes.of(qaId,
                updateReq.getTitle(),
                qaEntity.getContent(),
                updateReq.getStartAt(),
                updateReq.getEndAt(),
                qaEntity.getPayloadSurveyId());
    }

    /**
     * ɾ���ʾ�
     *
     * @param qaId �ʾ�id
     */
    @Transactional
    public void deleteQuestionnaire(Long qaId) {
        Optional<QuestionnaireEntity> qaEntityOp = qaEntityRepository.findByIdOptional(qaId);
        if (!qaEntityOp.isEmpty()) {
            QuestionnaireEntity qaEntity = qaEntityOp.get();
            // ɾ���ʾ�
            qaEntityRepository.deleteById(qaId);
            // ɾ������
            qaFeedbackEntityRepository.deleteBySurveyId(qaEntity.getPayloadSurveyId());
        }
    }

    /**
     * ��ѯ�ʾ�����
     *
     * @param qaId �ʾ�id
     * @return �ʾ�
     */
    public QuestionnaireRes getQuestionnaire(Long qaId) {
        QuestionnaireEntity qaEntity = qaEntityRepository.findById(qaId);
        if (qaEntity == null) {
            LOG.warnv("questionnaire does not exist, questionnaireId:{0}", qaId);
            throw new ServiceOperationException(ServiceError.QUESTIONNAIRE_NOT_EXIST);
        }
        return qaEntityToRes(qaEntity);
    }

    /**
     * ��ѯ�ʾ��б�
     *
     * @param subdomain �û�����
     * @param currentPage ��ǰҳ
     * @param pageSize ÿҳ����
     * @return �ʾ��б�
     */
    public PageListResult<QuestionnaireRes> listQuestionnaire(String subdomain, Integer currentPage, Integer pageSize) {
        List<QuestionnaireRes> qaResList = new ArrayList<>();
        // �жϣ����Ϊ�գ�������Ϊ1
        if (currentPage == null || currentPage <= 0) {
            currentPage = 1;
        }
        // 1.��ѯ�б�
        List<QuestionnaireEntity> qaEntities = qaEntityRepository.findAll().page(currentPage - 1, pageSize).list();
        qaEntities.forEach(qaEntity -> qaResList.add(qaEntityToRes(qaEntity)));
        // 2.��¼����
        Long totalCount = qaEntityRepository.count();
        // 3.��ѯ�û�����
        if (!CommonUtils.isNullOrEmpty(subdomain)) {
            List<QuestionnaireFeedbackEntity> qaFeedbackEntities = qaFeedbackEntityRepository.findBySubdomain(subdomain);
            Map<Long, QuestionnaireFeedbackEntity> qaFeedbackEntityMap = qaFeedbackEntities.stream().collect(
                    Collectors.toMap(QuestionnaireFeedbackEntity::getPayloadSurveyId, Function.identity(), (k1, k2) -> k2));
            // ����״̬
            qaResList.forEach(qaRes -> {
                OffsetDateTime now = OffsetDateTime.now();
                if (CommonUtils.isNotNull(qaRes.getStartAt()) && now.isBefore(qaRes.getStartAt())) {
                    qaRes.setState(QuestionnaireStateEnum.NOT_START.getName());
                } else if (CommonUtils.isNotNull(qaRes.getEndAt()) && now.isAfter(qaRes.getEndAt())) {
                    qaRes.setState(QuestionnaireStateEnum.HAS_END.getName());
                } else {
                    qaRes.setState(QuestionnaireStateEnum.IN_PROCESS.getName());
                }
                // �ѷ���
                QuestionnaireFeedbackEntity qaFeedbackEntity = qaFeedbackEntityMap.get(qaRes.getPayloadSurveyId());
                if (qaFeedbackEntity != null) {
                    qaRes.setState(QuestionnaireStateEnum.COMPLETED.getName());
                }
            });
        }
        return PageListResult.of(qaResList, PageInfo.of(totalCount, currentPage, pageSize));
    }

    @Transactional
    public FeedbackRes feedbackSave(FeedbackReq feedbackReq) {
        if (CommonUtils.isNull(feedbackReq.getPayload())) {
            LOG.infov("save questionnaire feedback begin, id:{0}, object:{1}, action:{2}, created_at:{3}", feedbackReq.getId(),
                    feedbackReq.getObject(), feedbackReq.getAction(), feedbackReq.getCreatedAt());
            return qaFeedbackReqToRes(feedbackReq);
        }
        LOG.infov("save questionnaire feedback begin, id:{0}, object:{1}, action:{2}, created_at:{3}, payload_survey_id:{4}, " +
                "payload_answer_id:{5}", feedbackReq.getId(), feedbackReq.getObject(), feedbackReq.getAction(), feedbackReq.getCreatedAt(),
                feedbackReq.getPayload().getSurveyId(), feedbackReq.getPayload().getAnswerId());
        FeedbackPayloadReq payload = feedbackReq.getPayload();
        // ��ѯ�ʾ��Ƿ����
        Optional<QuestionnaireEntity> qaEntityOp = qaEntityRepository.findBySurveyId(payload.getSurveyId());
        if (qaEntityOp.isEmpty()) {
            LOG.warnv("questionnaire does not exist, payloadSurveyId:{0}", payload.getSurveyId());
            throw new ServiceOperationException(ServiceError.QUESTIONNAIRE_NOT_EXIST);
        }
        // �����Ƿ��Ѵ���
        Optional<QuestionnaireFeedbackEntity> qaFeedbackEntityOp = qaFeedbackEntityRepository.findBySubdomainAndSurveyAndAnswerId(payload.getOpenId(),
                payload.getSurveyId(), payload.getAnswerId());
        if (!qaFeedbackEntityOp.isEmpty()) {
            LOG.warnv("payload answer already exist, subdomain:{0}, payloadSurveyId:{1}, payload_answer_id:{w}", payload.getOpenId(),
                    payload.getSurveyId(), payload.getAnswerId());
            throw new ServiceOperationException(ServiceError.PAYLOAD_ANSWER_ALREADY_EXIST);
        }
        OffsetDateTime answerAt = CommonUtils.dateTimeToOffsetDateTime(payload.getEndedAt());
        String answerDetail = operationUtils.objectToJson(payload.getAnswer());
        QuestionnaireFeedbackEntity qaFeedbackEntity = QuestionnaireFeedbackEntity.of(payload.getOpenId(),
                payload.getSurveyId(),
                payload.getAnswerId(),
                answerAt,
                answerDetail,
                null);
        qaFeedbackEntityRepository.persist(qaFeedbackEntity);
        LOG.infov("save questionnaire feedback succeed, id:{0}", feedbackReq.getId());
        return qaFeedbackReqToRes(feedbackReq);
    }

    private QuestionnaireRes qaEntityToRes(QuestionnaireEntity questionnaireEntity) {
        return QuestionnaireRes.of(questionnaireEntity.getId(),
                questionnaireEntity.getTitle(),
                questionnaireEntity.getContent(),
                questionnaireEntity.getStartAt(),
                questionnaireEntity.getEndAt(),
                questionnaireEntity.getPayloadSurveyId());
    }

    private QuestionnaireEntity qaReqToEntity(QuestionnaireReq questionnaireReq) {
        return QuestionnaireEntity.of(questionnaireReq.getTitle(),
                questionnaireReq.getContent(),
                questionnaireReq.getStartAt(),
                questionnaireReq.getEndAt(),questionnaireReq.getPayloadSurveyId());
    }

    private FeedbackRes qaFeedbackReqToRes(FeedbackReq feedbackReq) {
        return FeedbackRes.of(feedbackReq.getId(),
                feedbackReq.getObject(),
                feedbackReq.getAction(),
                feedbackReq.getCreatedAt());
    }
}
