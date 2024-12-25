// Engine.h
#pragma once
#include <memory>
#include <string>
#include <vector>
#include <unordered_map>

namespace GameEngine
{

    class Scene;
    class RenderSystem;
    class PhysicsSystem;

    class Engine
    {
    public:
        static Engine &getInstance();
        void initialize(const std::string &configPath);
        void run();
        void shutdown();

        std::shared_ptr<Scene> createScene(const std::string &name);
        void setActiveScene(const std::string &name);

    private:
        Engine() = default;
        ~Engine() = default;
        Engine(const Engine &) = delete;
        Engine &operator=(const Engine &) = delete;

        std::unordered_map<std::string, std::shared_ptr<Scene>> scenes_;
        std::shared_ptr<Scene> activeScene_;
        std::unique_ptr<RenderSystem> renderSystem_;
        std::unique_ptr<PhysicsSystem> physicsSystem_;
        bool isRunning_ = false;
    };

// Scene.h
#pragma once
#include <memory>
#include <string>
#include <vector>
#include <unordered_map>

    namespace GameEngine
    {

        class Entity;

        class Scene
        {
        public:
            explicit Scene(const std::string &name);
            std::shared_ptr<Entity> createEntity(const std::string &name);
            void removeEntity(const std::string &name);
            void update(float deltaTime);
            void render();

        private:
            std::string name_;
            std::vector<std::shared_ptr<Entity>> entities_;
            std::unordered_map<std::string, std::shared_ptr<Entity>> entityMap_;
        };

// Entity.h
#pragma once
#include <memory>
#include <string>
#include <vector>

        namespace GameEngine
        {

            class Component;

            class Entity
            {
            public:
                explicit Entity(const std::string &name);

                template <typename T>
                std::shared_ptr<T> addComponent();

                template <typename T>
                std::shared_ptr<T> getComponent();

                void update(float deltaTime);

            private:
                std::string name_;
                std::vector<std::shared_ptr<Component>> components_;
            };

// Component.h
#pragma once

            namespace GameEngine
            {

                class Component
                {
                public:
                    virtual ~Component() = default;
                    virtual void initialize() = 0;
                    virtual void update(float deltaTime) = 0;
                };

// RenderSystem.h
#pragma once
#include <memory>

                namespace GameEngine
                {

                    class Scene;

                    class RenderSystem
                    {
                    public:
                        virtual ~RenderSystem() = default;
                        virtual void initialize() = 0;
                        virtual void render(const Scene &scene) = 0;
                        virtual void cleanup() = 0;
                    };

// PhysicsSystem.h
#pragma once
#include <memory>

                    namespace GameEngine
                    {

                        class Scene;

                        class PhysicsSystem
                        {
                        public:
                            virtual ~PhysicsSystem() = default;
                            virtual void initialize() = 0;
                            virtual void update(float deltaTime, Scene &scene) = 0;
                            virtual void cleanup() = 0;
                        };

                    }
                } // namespace GameEngine