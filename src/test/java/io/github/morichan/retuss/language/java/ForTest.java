package io.github.morichan.retuss.language.java;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ForTest {
    For forClass;

    @Nested
    class 正しい使い方の場合 {
        @BeforeEach
        void setup() { forClass = new For("int i=0", "i<10", "i++"); }

        @Test
        void for文を返す() {
            String expected = "for (int i=0; i<10; i++) {\n        }\n";
            assertThat(forClass.getStatement()).isEqualTo(expected);
        }
    }

    @Nested
    class 正しい使い方ではない場合 {
        @BeforeEach
        void setup() { forClass = new For("int i=0", "i<10", "i++"); }

        @Test
        void numLoopにnullを設定したら例外を投げる() {
            assertThatThrownBy(() -> forClass.setNumLoop(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void statementsにnullを追加したら例外を投げる() {
            assertThatThrownBy(() -> forClass.addStatement(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }


}
