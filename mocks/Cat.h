#ifndef CAT_H
#define CAT_H

#include "Animal.h"
#include "Food.h"
#include <list>

class Toy; // 前方宣言

class Cat : public Animal
{ // 継承関係
private:
    std::list<Toy> toys;  // 別のコレクション型（多重度: *)
    Toy favoriteToy;      // コンポジション関係
    static Cat *instance; // static メンバー

public:
    Cat(const std::string &name);
    void eat(Food *food) override;
    void play(Toy &toy); // 参照パラメータ
};

#endif