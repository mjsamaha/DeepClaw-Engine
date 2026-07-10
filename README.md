# 🦞 DeepClaw Engine

> **A lightweight 2D Java game engine built for game jams, indie development, and rapid prototyping.**

DeepClaw is a custom Java game framework designed around a clean modular architecture using a pragmatic **Hybrid ECS + Object-Oriented Composition** approach.

The goal is simple:

> **The engine should never know what game it is running.**

DeepClaw provides reusable systems for building games while maintaining the flexibility and readability of traditional Java development.

🎮 Designed for:
- Game jams
- Indie projects
- 2D games
- Rapid experimentation
- Learning engine architecture

DeepClaw will be my primary framework used to create and publish small or mid-sized games on [Itch.io](https://itch.io/).

---

# 🧠 Philosophy

DeepClaw follows a strict layered architecture:

```

Game/Sandbox
↓
Runtime
↓
Engine

```

Each layer has a specific responsibility.

| Layer | Responsibility |
|---|---|
| Engine | Reusable low-level systems |
| Runtime | Shared gameplay infrastructure |
| Game | Project-specific content |

This dependency flow keeps DeepClaw reusable and prevents game-specific logic from leaking into the engine.

---

# 📂 Repository Structure

```

src/
├── engine/
├── runtime/
└── game/

```

---

# ⚙️ Engine Layer

The **engine** contains only reusable systems.

The engine should never contain:

❌ Player logic  
❌ Enemy behaviour  
❌ Weapons  
❌ Levels  
❌ Story content  
❌ Game-specific UI  

Instead, it provides the foundation that powers every game.

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

# 🔄 Runtime Layer

The runtime sits between the engine and the game.

It contains reusable gameplay infrastructure shared between projects.

Examples:

✅ Scene management  
✅ Entity factories  
✅ Prefabs  
✅ Common components  
✅ Shared systems  
✅ Bootstrap logic  

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

> The runtime is reusable, but it is not part of the core engine.

---

# 🎮 Sandbox/Game Layer

The game package contains everything specific to an individual project.

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

The engine should never depend on the game layer.

---

# 🏗️ Architecture

DeepClaw follows a modular architecture:

```

```
             Engine

    ┌───────────────────┐
    │ ECS               │
    │ Rendering         │
    │ Physics           │
    │ Audio             │
    │ Input             │
    │ Resources         │
    │ Services          │
    └───────────────────┘

              ↓

            Runtime

    ┌───────────────────┐
    │ Prefabs           │
    │ Scene Framework   │
    │ Entity Factory    │
    │ Shared Components │
    └───────────────────┘

              ↓

             Game

    ┌───────────────────┐
    │ Gameplay          │
    │ Levels            │
    │ Entities          │
    │ Mechanics         │
    │ Content           │
    └───────────────────┘
```

```

---

# 🧩 Hybrid ECS Architecture

DeepClaw intentionally avoids a fully data-oriented ECS.

Instead, it combines:

- Entity Component System architecture
- Java object-oriented design
- Composition over inheritance

Entities are simple containers of reusable components:

```

Entity
├── TransformComponent
├── SpriteComponent
├── ColliderComponent
├── HealthComponent
└── AIComponent

```

Benefits:

✨ Flexible gameplay design  
✨ Easier debugging  
✨ Cleaner Java code  
✨ Reusable systems  
✨ Good performance for 2D games  

---

# 🧱 Design Principles

## Composition Over Inheritance

DeepClaw avoids large inheritance trees:

```

Player
↓
Character
↓
LivingEntity
↓
Entity

```

Instead:

```

Entity

* TransformComponent
* HealthComponent
* SpriteComponent
* InputComponent

```

---

## 🔌 Service-Based Architecture

Engine-wide systems are exposed through services.

Examples:

- Renderer
- Audio Manager
- Input Manager
- Asset Manager
- Event Bus
- Save Manager
- Logger

This keeps systems loosely coupled and replaceable.

---

## 📦 Resource Pipeline

All resources flow through a centralized pipeline:

```

ResourceLoader
↓
AssetManager
↓
Game

```

Games never directly manage file loading.

---

## 🔒 Dependency Rules

DeepClaw enforces one-way dependencies:

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

# 🎯 Goals

DeepClaw aims to be:

- 🪶 Lightweight
- 🧩 Modular
- 📚 Easy to understand
- 🔧 Easy to extend
- 🎮 Game-jam friendly
- 🚀 Fast to prototype with
- ☕ Built with modern Java practices

---

# 🎮 Target Projects

DeepClaw is designed for:

- Platformers
- Top-down RPGs
- Puzzle games
- Roguelikes
- Strategy games
- Arcade games
- Game jam projects
- Small indie games

---

# 🛠️ Technology Stack

| Technology | Purpose |
|-|-|
| ☕ Java | Core language |
| 🖼️ AWT/Swing | Rendering foundation |
| ⚙️ Gradle | Build system |
| 🧩 Hybrid ECS | Entity architecture |
| 🏗️ OOP Composition | Design philosophy |

---

# 📌 Project Status

🚧 **Currently in active development**

DeepClaw is being built incrementally through experiments, prototypes, and game projects. Features are continuously extracted into reusable engine modules.

---

# 🗺️ Roadmap

## Phase 1 — Foundation

🚧 No public releases during this phase.

### Core Engine Mechanics

- Core engine architecture
- Entity Component System
- Rendering pipeline
- Input management
- Asset handling
- Physics implementations
- UI management
- Events
- Animation systems
- Particle engine
- Audio framework
- Reusable AI Game mechanics
- Save utilities
- Factory system
- Util and constants
- Logging/Debug tools

### Runtime Mechanics

INFO --> `This roadmap is under construction`


### Sandbox/Game

INFO --> `This roadmap is under construction`
