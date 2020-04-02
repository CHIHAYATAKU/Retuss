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
        void setup() { ifClass = new If(""); }

        @Test
        void if文を返す() {
            ifClass.setCondition("a<10");
            String expected = "if (a<10) {\n        } ";
            assertThat(ifClass.getStatement()).isEqualTo(expected);
        }

        @Test
        void ifとelseを返す() {
            ifClass.setCondition("a<10");
            ifClass.setHasElse(Boolean.TRUE);
            String expected = "if (a<10) {\n        } else {\n        }\n";
            assertThat(ifClass.getStatement()).isEqualTo(expected);
        }

        @Test
        void ifとelseifを返す() {
            ifClass.setCondition("a<10");
            ifClass.addElseStatement(new If("a<20"));
            String expected = "if (a<10) {\n        } else if (a<20) {\n        } ";
            assertThat(ifClass.getStatement()).isEqualTo(expected);
        }

        @Test
        void ifとelseifとelseを返す() {
            ifClass.setCondition("a<10");
            If ifClass2 = new If("a<20");
            ifClass2.setHasElse(Boolean.TRUE);
            ifClass.addElseStatement(ifClass2);
            String expected = "if (a<10) {\n        } else if (a<20) {\n        } else {\n        }\n";
            assertThat(ifClass.getStatement()).isEqualTo(expected);
        }
    }

    @Nested
    class 正しい使い方ではない場合 {
        @BeforeEach
        void setup() { ifClass = new If(""); }

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
