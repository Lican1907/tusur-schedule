---
name: android-glance-widget
description: >-
  Use this skill when developing, styling, debugging, or updating the Android home screen
  widget using Jetpack Glance (ScheduleWidget.kt, ScheduleWidgetTheme.kt, GlanceAppWidget).
---

# Android Glance Widget Best Practices (tusur-schedule)

Этот скилл содержит правила и готовые сниппеты для работы с виджетом расписания ТУСУР на базе Jetpack Glance.

---

## 1. Главные ограничения Jetpack Glance

1. **Не все Compose-модификаторы поддерживаются**:
   * Glance транслирует код в `RemoteViews` для системного лаунчера.
   * Использовать ТОЛЬКО импорты из `androidx.glance.*` (например `androidx.glance.layout.*`, `androidx.glance.text.*`).
   * **ЗАПРЕЩЕНО**: `androidx.compose.ui.Modifier.clickable` (вместо этого использовать `androidx.glance.action.clickable` или `actionRunCallback`).
   * **ЗАПРЕЩЕНО**: Анимации и Canvas-рисование напрямую в коде Glance.

2. **Обновление состояния виджета**:
   Для обновления виджета из фона (например, из `ScheduleSyncWorker.kt` или `MainActivity.kt`):
   ```kotlin
   import androidx.glance.appwidget.updateAll
   
   // Обновление всех экземпляров виджета
   ScheduleWidget().updateAll(context)
   ```

3. **Стилизация карточек**:
   * Все цвета фона карточек пар (лекция, практика, лаба, отмена) берутся из `android/app/src/main/res/drawable/`.
   * Для отмененных пар используется визуальная перечеркнутая полоса и стиль отмены (`is_cancelled = true`).

---

## 2. Команда проверки сборки виджета
```powershell
cd android ; .\gradlew :app:compileDebugKotlin ; cd ..
```
