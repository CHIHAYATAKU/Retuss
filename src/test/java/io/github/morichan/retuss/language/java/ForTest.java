package io.github.morichan.retuss.language.java;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ForTest {
    For forClass;
    LocalVariableDeclaration lvd;

    @Nested
    class 正しい使い方の場合 {
        @BeforeEach
        void setup() {
            forClass = new For("int i=0", "i<10", "i++");
            lvd = new LocalVariableDeclaration(new Type("int"), "var");
        }

        @Test
        void 初期化式を返す() {
            String expected = "int i=0";
            assertThat(forClass.getForInit()).isEqualTo((expected));
        }

        @Test
        void 継続条件式を返す() {
            String expected = "i<10";
            assertThat(forClass.getExpression()).isEqualTo((expected));
        }

        @Test
        void 更新式を返す() {
            String expected = "i++";
            assertThat(forClass.getForUpdate()).isEqualTo((expected));
        }

        @Test
        void for文を返す() {
            String expected = "for (int i=0; i<10; i++) {\n        }\n";
            assertThat(forClass.getStatement()).isEqualTo(expected);
        }

        @Test
        void numLoopの設定と取得() {
            String numLoop = "10";
            forClass.setNumLoop((numLoop));
            assertThat(forClass.getNumLoop()).isEqualTo(numLoop);
        }

        @Test
        void statementの追加と取得() {
            forClass.addStatement(lvd);
            assertThat(forClass.getStatements().get(0)).isEqualTo(lvd);
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
