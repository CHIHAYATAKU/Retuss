#ifndef CAT_H
#define CAT_H

#include "Animal.h"
#include "Food.h"
#include <list>

class Toy; // 前方宣言

class Cat : public Animal
{ // 継承関係
private:
    std::list<Toy> toys;
    Toy favoriteToy;
    static Cat *instance;

public:
    Cat(const std::string &name);
    void eat(Food *food) override;
    void play(Toy &toy);
};

#endif