#ifndef ANIMAL_H
#define ANIMAL_H

#include <string>
#include <vector>

class Food; // 前方宣言

class Animal
{
protected:
    std::string name;
    int age;
    Food *currentFood;       // ポインタによる依存関係
    std::vector<Food> foods; // コレクション（多重度: *)
    Animal *parent;          // 自己参照（依存関係）
    const Animal &mate;      // 参照による依存関係
    Food foodArray[5];       // 固定長配列（多重度: 5）

public:
    Animal(const std::string &name);
    virtual ~Animal();

    virtual void eat(Food *food) = 0; // 仮想メソッド
    void sleep();
};

#endif