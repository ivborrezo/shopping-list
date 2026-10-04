package dev.ivborrezo.shoppinglist.product.service.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void generate_returnsNonNullUuid() {
    UUID uuid = UuidV7.generate();

    assertThat(uuid).isNotNull();
  }

  @Test
  void generate_returnsUuidVersion7() {
    UUID uuid = UuidV7.generate();

    assertThat(uuid.version()).isEqualTo(7);
  }

  @Test
  void generate_returnsDistinctUuidsOnConsecutiveCalls() {
    UUID first = UuidV7.generate();
    UUID second = UuidV7.generate();

    assertThat(second).isNotEqualTo(first);
  }
}
