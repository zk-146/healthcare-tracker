# Healthcare Activity Tracker — What It Does (In Plain English)

*A simple explanation for anyone, no technical background needed.*

---

## The one-sentence version

This is the **"engine room" behind a fitness app** — the part that remembers your
workouts, keeps your account safe, adds up your progress, and celebrates your streaks.
It's like the kitchen of a restaurant: customers never see it, but nothing reaches
the table without it.

---

## What is it, really?

Think of an app like **Fitbit, Strava, or Apple Health**. When you go for a run and it
shows up in your phone with your distance, calories, and a little "7-day streak!" badge —
something behind the scenes had to *store* that run, *protect* it so only you can see it,
and *do the math* to show your totals.

**This project is that behind-the-scenes part.** It does not have buttons or screens of
its own. It's the reliable worker that a phone app or website would talk to.

> 📱 **Important:** On its own, you can't "open" this and use it like an app.
> It needs a phone app or website built on top of it — the same way a car engine
> needs a body, seats, and a steering wheel before anyone can drive it.

---

## What can it do? (The 5 main jobs)

### 1. 🔐 Keep your account safe (Log in / Sign up)
- You can **create an account**, **log in**, and **log out**.
- Your password is scrambled so even the people running the system can't read it.
- It uses secure digital "passes" (like a wristband at a concert) that prove it's
  really you — and it can **instantly cancel that pass** if you log out, so a lost
  phone can't keep you logged in.

### 2. 🏃 Record your workouts
- Log activities like **walking, running, yoga, cycling, swimming, strength training**.
- For each one it remembers the details: **how long, how far, steps, heart rate,
  calories, and any notes** you add.
- You can **view, edit, or delete** any of your past workouts.
- Your data is private — the system makes sure you can only ever see *your own* workouts.

### 3. 👤 Keep your profile
- Stores your basics: **name, height, weight, date of birth**.
- Built carefully so that if you update it on two devices at once, your data
  doesn't get scrambled.

### 4. 📊 Add up your progress (Summaries)
- Automatically totals your **calories, distance, and steps**.
- Gives you views for **today, this week, this month, or any date range**.
- Breaks it down by activity type (e.g., "you ran 3 times and did yoga twice").
- All the adding-up is done correctly and quickly, even with lots of data.

### 5. 🔥 Track streaks & celebrate milestones
- Counts how many **days in a row** you've been active (your "streak").
- When you hit a milestone — **3, 7, 14, 30, 60, 100, or 365 days** — it notices and
  records the achievement.
- It's smart enough to handle different time zones and to **not congratulate you twice**
  for the same milestone.

---

## Is it any good? Is it "real"?

**Yes — this is genuine, working software, not a fake demo.**

- The calculations are real (it truly adds up your numbers and counts your streaks
  correctly).
- The security is done properly (the way a real, professional app would do it).
- It's built to handle many users at once without slowing down or breaking.
- It comes with **automated safety checks** that test itself and scan for security
  problems — a sign of careful, professional work.

**Honest limitations** (so you have the full picture):

| What you might expect | The reality today |
|---|---|
| A screen/app to tap on | ❌ Not included — this is only the behind-the-scenes engine |
| It calculates calories for you | ⚠️ Partly — it stores the calorie number the app sends it; the auto-calculation isn't switched on yet |
| It sends you a "Congrats!" notification | ⚠️ It *records* the milestone, but the actual push/email notification is a placeholder for now |

---

## The bottom line

Think of it as a **professionally-built engine without the car around it yet.**

- It is **not useless** — the engine genuinely runs and does real work.
- It is **not a finished product** you can hand to a friend to use — it still needs a
  screen/app built on top, and a couple of features (auto calorie math, real
  notifications) are wired up but not fully turned on.

It's best described as a **solid, professional-quality foundation** for a fitness
tracking app — the hard, invisible part is done well; the visible part (and a few
finishing touches) would come next.

---

*Report generated 2026-06-29.*
