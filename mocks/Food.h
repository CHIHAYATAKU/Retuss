#ifndef FOOD_H
#define FOOD_H

#include <string>

class Food
{
private:
    std::string name;
    double calories;

public:
    Food(const std::string &name, double calories);
    std::string getName() const;
};

#endif