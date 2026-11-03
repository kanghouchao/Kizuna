package com.kizuna.survey.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.kizuna.shared.persistence.FluentPageCaptor;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyHistoryRepository;
import com.kizuna.survey.domain.SurveyRevisionRepository;
import com.kizuna.survey.domain.SurveySeriesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SurveyReadServicePagingTest {
  private static final String ID = "42";

  @Mock SurveySeriesRepository surveys;
  @Mock SurveyRevisionRepository revisions;
  @Mock SurveyAnswerRepository answers;
  @Mock SurveyHistoryRepository history;
  @Mock SurveyActors actors;
  @InjectMocks SurveyReadService service;

  private final FluentPageCaptor pages = new FluentPageCaptor();

  @Test
  void surveyListEndsInIdInTheRequestedDirection() {
    doAnswer(pages.answer()).when(surveys).findBy(any(Specification.class), any());

    service.list(1L, "staff", PageRequest.of(2, 10), null, null, null, "CREATED_DESC");

    assertThat(pages.captured().getSort())
        .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    assertThat(pages.captured().getPageNumber()).isEqualTo(2);
  }

  @Test
  void answerListEndsInIdInTheRequestedDirection() {
    when(revisions.existsByIdAndStoreIdAndSurveyId(ID, 1L, ID)).thenReturn(true);
    doAnswer(pages.answer()).when(answers).findBy(any(Specification.class), any());

    service.answers(1L, "staff", ID, ID, PageRequest.of(0, 10), null, null, "RECEIVED_ASC");

    assertThat(pages.captured().getSort())
        .containsExactly(Sort.Order.asc("receivedAt"), Sort.Order.asc("id"));
  }
}
