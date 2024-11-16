#include "Cat.h"
#include "Toy.h"

Cat::Cat(const std::string &name) : Animal(name)
{
    // インスタンス化によるコンポジション関係
    Toy *newToy = new Toy("Ball"); // 動的生成による依存関係
    favoriteToy = Toy("Mouse");    // コンポジション関係
    toys.push_back(Toy("String")); // コレクションを使用
}

void Cat::eat(Food *food)
{
    // ポインタパラメータの使用
    if (food != nullptr)
    {
        currentFood = food;
    }
}

void Cat::play(Toy &toy)
{
    // 参照パラメータの使用
    toy.use();
}