#ifndef TOY_H
#define TOY_H

#include <string>

class Toy
{
private:
    std::string name;
    bool isBroken;

public:
    Toy(const std::string &name);
    void use();
};

#endif