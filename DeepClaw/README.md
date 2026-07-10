# DeepClaw

> **DeepClaw** is a lightweight 2D Java game engine designed for rapid game development, game jams, and indie projects. It follows a clean, modular architecture with a pragmatic **Hybrid ECS + OOP composition** approach, making it easy to build reusable gameplay systems without sacrificing Java's object-oriented strengths.

DeepClaw is the primary custom framework used to create and submit small games to [Itch.io](https://itch.io/).

---

# Philosophy

DeepClaw is built around a simple idea:

> **The engine should never know what game it's running.**

The repository is divided into three layers:

```
Game
   ↓
Runtime
   ↓
Engine
```

This strict dependency flow keeps the engine reusable while allowing games to evolve independently.

---

# Repository Structure

```
src/
├── engine/
├── runtime/
└── game/
```

---

# Engine

The **engine** contains only reusable systems.

It should never reference game-specific concepts such as:

* Players
* Enemies
* Weapons
* Levels
* UI Screens
* Story content

Instead, it provides the core framework that powers every game.

## Engine Modules

```
engine
├── core
├── config
├── services
├── window
├── ecs
├── scene
├── rendering
├── assets
├── resources
├── input
├── physics
├── world
├── audio
├── ui
├── events
├── particles
├── ai
├── save
├── logging
└── util
```

---

# Runtime

The **runtime** sits between the engine and the game.

It contains reusable gameplay infrastructure shared between projects.

Examples:

* Scene framework
* Bootstrap logic
* Entity factories
* Prefabs
* Common components
* Shared gameplay systems

```
runtime
├── bootstrap
├── scenes
├── prefabs
├── factory
├── components
├── systems
├── common
└── game
```

The runtime is reusable but is **not part of the engine itself**.

---

# Game

The **game** package contains everything specific to an individual project.

```
game
├── GameMain
├── scenes
├── entities
├── systems
├── levels
├── items
├── weapons
├── content
└── constants
```

Nothing inside the engine should depend on this package.

---

# Architecture

DeepClaw follows a modular architecture built around reusable systems.

```
Engine
│
├── ECS
├── Rendering
├── Physics
├── Audio
├── Input
├── Scene Management
├── Resources
└── Services

        ↓

Runtime
│
├── Prefabs
├── Scene Framework
├── Entity Factory
└── Shared Components

        ↓

Game
│
├── Gameplay
├── Levels
├── Entities
├── Mechanics
└── Content
```

---

# Hybrid ECS

DeepClaw intentionally avoids a fully data-oriented ECS.

Instead, it combines:

* Entity Component System architecture
* Java object-oriented programming
* Composition over inheritance

Each entity acts as a container for components.

Example:

```
Entity
 ├── TransformComponent
 ├── SpriteComponent
 ├── ColliderComponent
 ├── HealthComponent
 └── AIComponent
```

Benefits:

* Flexible gameplay composition
* Easier debugging
* Readable Java code
* Good performance for 2D games

---

# Design Principles

## Composition Over Inheritance

Instead of large inheritance trees:

```
Player
    ↓
Character
    ↓
LivingEntity
    ↓
Entity
```

DeepClaw favors:

```
Entity
 + TransformComponent
 + HealthComponent
 + SpriteComponent
 + InputComponent
```

---

## Service-Oriented Engine

Global systems are exposed through engine services.

Examples:

* Renderer
* Audio
* Input
* Asset Manager
* Event Bus
* Save Manager
* Logger

This keeps systems loosely coupled and replaceable.

---

## Resource Pipeline

Every resource flows through a single loading pipeline.

```
ResourceLoader
        ↓
AssetManager
        ↓
Game
```

The game never loads files directly.

---

## Clean Dependency Direction

DeepClaw enforces one-way dependencies.

```
Game
    ↓
Runtime
    ↓
Engine
```

Never:

```
Engine → Runtime
Engine → Game
```

---

# Goals

DeepClaw is designed to be:

* Lightweight
* Easy to understand
* Easy to extend
* Game jam friendly
* Suitable for indie projects
* Cleanly architected
* Built with modern Java practices

---

# Target Projects

DeepClaw is ideal for:

* 2D platformers
* Top-down RPGs
* Puzzle games
* Roguelikes
* Strategy games
* Arcade games
* Game jam projects
* Small-to-medium indie games

---

# Technology

* Java
* Java AWT/Swing
* Gradle
* Hybrid ECS Architecture
* Composition-Based Design

---

# Future Roadmap

---

#### Early Development Period

**This period will not produce any releases**




Planned features:

* Scene transitions
* Tilemap support
* Animation system
* Particle engine
* Audio improvements
* Physics enhancements
* Serialization and save system
* Resource hot reloading
* Debug overlays
* UI framework
* Editor tooling
* Plugin system

---

# Project Status

DeepClaw is currently under active development.

The engine is being built incrementally through game projects and experiments, with reusable features continuously extracted into engine modules.

The goal is to provide a personal lightweight Java framework for creating and publishing small games.
