package dev.ivborrezo.shoppinglist.list.service.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Tests unitarios de {@link CorrelationIdFilter}. */
class CorrelationIdFilterTest {

  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private static final String MDC_KEY = "correlationId";

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void doFilter_withCorrelationIdHeader_reusesItInMdcAndResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CORRELATION_ID_HEADER, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] correlationIdDuringChain = new String[1];
    FilterChain chain = (req, res) -> correlationIdDuringChain[0] = MDC.get(MDC_KEY);

    filter.doFilter(request, response, chain);

    assertThat(correlationIdDuringChain[0]).isEqualTo("abc-123");
    assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo("abc-123");
  }

  @Test
  void doFilter_withoutCorrelationIdHeader_generatesUuid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] correlationIdDuringChain = new String[1];
    FilterChain chain = (req, res) -> correlationIdDuringChain[0] = MDC.get(MDC_KEY);

    filter.doFilter(request, response, chain);

    String responseCorrelationId = response.getHeader(CORRELATION_ID_HEADER);
    assertThatNoException().isThrownBy(() -> UUID.fromString(responseCorrelationId));
    assertThat(correlationIdDuringChain[0]).isEqualTo(responseCorrelationId);
  }

  @Test
  void doFilter_afterChain_clearsMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(MDC.get(MDC_KEY)).isNull();
  }
}
