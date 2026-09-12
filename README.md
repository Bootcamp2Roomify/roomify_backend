# Roomify

Roomify is an AI-powered room interior and DIY planning application designed for budget-conscious users.

## Problem Statement

Many people want to redesign their rooms but cannot easily visualize the final result, control the total cost, or determine which furniture should be kept, replaced, or removed. Professional interior-design services may also be too expensive for students, renters, and users with limited budgets.

Roomify allows users to upload an image of their actual room and receive a personalized redesign concept based on their existing furniture, preferred style, budget, rental status, and practical requirements.

## Target Users

Roomify is primarily designed for:

- College students
- Renters
- Young adults
- Low-budget users
- Users without interior-design experience

## MVP Features

- User registration and secure login
- Room project creation
- Room image upload
- AI room analysis and furniture detection
- Furniture review
- Keep, Replace, Remove, or Unsure decisions
- Style, color, budget, and requirement input
- Personalized AI-generated room redesign
- Furniture and decoration recommendations
- Beginner-level DIY suggestions
- Estimated project cost
- Saved room projects and dashboard

## Technology Stack

### Frontend

- Next.js
- React
- TypeScript
- Tailwind CSS

### Backend

- Java
- Spring Boot
- REST API

### Computer Vision

- Python
- FastAPI
- Object detection and image analysis

### Database and Cloud

- PostgreSQL
- Cloud object storage
- AWS Secrets Manager

## Repository Structure

Roomify uses a monorepo because the frontend, backend, and computer-vision components belong to one MVP and are being developed by a small student team.

```text
roomify/
├── frontend/
├── backend/
├── cv/
├── docs/
├── .github/
├── .env.example
├── .gitignore
├── CONTRIBUTING.md
└── README.md
