package io.github.morichan.retuss.language.java;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IfTest {

    If ifClass;

    @Nested
    class 正しい使い方の場合 {
        @BeforeEach
        void setup() { ifClass = new If(); }

        @Test
        void if文を返す() {
            ifClass.setCondition("a<10");
            String expected = "if (a<10) {\n        } ";
            assertThat(ifClass.getStatement()).isEqualTo(expected);
        }

        @Test
        void ifとelseを返す() {
            ifClass.setCondition("a<10");
//            ifClass.addElseStatement();
        }
    }

    @Nested
    class 正しい使い方ではない場合 {
        @BeforeEach
        void setup() { ifClass = new If(); }

        @Test
        void statementsにnullを追加したら例外を投げる() {
            assertThatThrownBy(() -> ifClass.addStatement(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void elseStatementsにnullを追加したら例外を投げる() {
            assertThatThrownBy(() -> ifClass.addElseStatement(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
