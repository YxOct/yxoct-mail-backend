package com.yxoct.mail.domain.mail;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import java.util.Arrays;

public record MailSort(Field field, Direction direction) {

  public MailSort {
    if (field == null || direction == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  public static MailSort defaultSort() {
    return new MailSort(Field.RECEIVED_AT, Direction.DESC);
  }

  public static MailSort parse(String field, String direction) {
    return new MailSort(Field.parse(field), Direction.parse(direction));
  }

  public enum Field {
    RECEIVED_AT("receivedAt"),
    SENT_AT("sentAt"),
    SUBJECT("subject"),
    FROM("from"),
    TO("to"),
    SIZE("size");

    private final String jmapProperty;

    Field(String jmapProperty) {
      this.jmapProperty = jmapProperty;
    }

    public String jmapProperty() {
      return jmapProperty;
    }

    private static Field parse(String value) {
      return Arrays.stream(values())
          .filter(field -> field.jmapProperty.equalsIgnoreCase(value))
          .findFirst()
          .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));
    }
  }

  public enum Direction {
    ASC("asc", true),
    DESC("desc", false);

    private final String value;
    private final boolean ascending;

    Direction(String value, boolean ascending) {
      this.value = value;
      this.ascending = ascending;
    }

    public boolean ascending() {
      return ascending;
    }

    private static Direction parse(String value) {
      return Arrays.stream(values())
          .filter(direction -> direction.value.equalsIgnoreCase(value))
          .findFirst()
          .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));
    }
  }
}
