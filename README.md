# 🩸 BloodCare - Blood Donation Support System

> **SWP391 Course Project** | A web-based application designed to streamline blood donation registration, appointment scheduling, and record management connecting Donors, Medical Staff, and Administrators.

---

## 📌 Overview

**BloodCare** is an end-to-end software solution for blood donation management. It simplifies the registration process for donors while optimizing reception, health screening tracking, and data management for medical centers.

### ✨ Key Features

* **🔐 Authentication & Authorization:**
  * Secure Login/Register using **JWT (JSON Web Token)**.
  * Quick sign-in via **Google OAuth2**.
  * **Role-based Access Control (RBAC)**: *Admin*, *Medical Staff*, and *Donor*.

* **🩸 Donor & Registration Management:**
  * Browse upcoming blood donation drives/events and register appointments online.
  * Track personal donation history and application status.
  * Automatic email confirmations & notification system via **Spring Mail**.

* **🩺 Medical Staff Operations:**
  * Review and approve donor registrations.
  * Record health screening indicators (blood pressure, hemoglobin, blood type, etc.) and actual blood volume donated.
  * Manage donor medical history records.

* **📊 Admin Dashboard & Reporting:**
  * User and staff account management.
  * Analytics dashboard and data export to **Excel** format (using Apache POI).
  * Interactive API documentation integrated with **Swagger / OpenAPI**.

* **💬 Donor Feedback System:**
  * Collect ratings and feedback from donors after each donation session.

---

## 🛠️ Tech Stack

### 🔹 Back-end
* **Language & Framework:** Java 21, Spring Boot 3.5
* **Security:** Spring Security, JWT (jjwt 0.12.6), Spring OAuth2 Client
* **Database:** MySQL, Spring Data JPA, Hibernate
* **Utilities:** Apache POI (Excel export), Spring Mail, Swagger UI (springdoc-openapi), Lombok

### 🔹 Front-end
* **Framework / Core:** React 19, TypeScript, Vite
* **Routing:** React Router DOM (v7)
* **Styling:** Tailwind CSS, Tailwind Animate, Lucide React (Icons)
* **Build Tool:** Vite

---

## 🚀 Getting Started

### Prerequisites
* **JDK:** Java 21 or higher
* **Node.js:** v18+ and npm / yarn
* **Database:** MySQL Server 8.0+

### 1️⃣ Run Back-end
```bash
cd back-end/blood-donation-support-system
# Configure your MySQL connection in src/main/resources/application.properties
./mvnw spring-boot:run
```
*API Documentation available at:* `http://localhost:8080/swagger-ui.html`

### 2️⃣ Run Front-end
```bash
cd front-end
npm install
npm run dev
```
*Application UI running at:* `http://localhost:5173`

---

👤 **Author:** Nguyen Tran Viet An
