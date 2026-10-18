# Задача TS-008: Редизайн Glance-виджета расписания по концепту (Full Agenda & Modern Cards)

## 1. Метаданные
- **ID:** `TS-008`
- **Домен:** `Android`
- **Исполнитель:** `Contractor-Android`
- **Статус:** `Done`
- **Связанные скиллы:** `android-glance-widget`, `task-accelerator`, `task-reviewer`

---

## 2. Описание задачи и целевой концепт

Требуется выполнить полный редизайн домашнего виджета Jetpack Glance (`ru.lican1907.tusurschedule.widget`) в соответствии с концептуальным макетом (см. референс макета):
1. **Шапка виджета (`WidgetHeader.kt`)**:
   - Верхний статус: `Сегодня · пар больше нет` / `Сегодня · 2 пары` / `Сейчас · идёт пара` / `Сегодня · пар нет`.
   - Дата: Полное название месяца в родительном падеже: `Чт, 15 октября` (крупный жирный белый текст).
   - Чип группы справа: `ИВТ-21` (или с подгруппой `ИВТ-21 · п/г 1`) в закругленном темном контейнере.
   - Круглая кнопка обновления (34dp) с символом `↻`.
2. **Hero-карточка (`WidgetHeroCard.kt` & `ScheduleWidgetUtils.kt`)**:
   - Верхняя строка: Слева тег `СЛЕДУЮЩАЯ · ЗАВТРА` / `СЛЕДУЮЩАЯ · СЕГОДНЯ` / `СЕЙЧАС · ИДЁТ` / `СЛЕДУЮЩАЯ · ПОНЕДЕЛЬНИК` (all-caps). Справа таймер `через 22 ч 40 мин` / `через 35 мин` / `осталось 40 мин` (строчными буквами в русском формате).
   - Название предмета: Крупный жирный белый текст (`22sp`).
   - Нижняя строка: `🕒 10:40–12:10   📍 ГК 323   Практика` (иконки времени и локации в одной строке).
3. **Состояние "Сегодня" (`ScheduleWidget.kt` / `WidgetDayHeader.kt`)**:
   - Если сегодня все пары закончились (или пар не было): отображать разделитель `Сегодня чт, 15 окт` и компактную карточку отдыха `🌙 Пары закончились — отдыхай` (или `🌙 Пар нет — отдыхай`).
   - Если сегодня есть оставшиеся пары: отображать заголовок и карточки оставшихся пар.
4. **Заголовки дней в списке (`WidgetDayHeader.kt`)**:
   - Слева: Название дня (жирный) + дата (приглушенный серый).
     - Для Сегодня: `Сегодня чт, 15 окт`
     - Для Завтра: `Завтра пт, 16 окт`
     - Для других дней: `Суббота 17 окт`, `Понедельник 19 окт` (без дублирования дня недели).
   - Справа: Количество пар (`2 пары`, `3 пары`) приглушенным шрифтом.
5. **Карточки занятий (`WidgetLessonCard.kt`)**:
   - Колонка времени (слева): `10:40` (15sp, bold, белый), под ним `12:10` (12sp, muted gray).
   - Вертикальный цветной индикатор (3dp) по типу пары.
   - Правая часть:
     - Верхняя строка: Название предмета + бейдж `2 подгруппы` (темная плашка, светлый текст).
     - Нижняя строка: Тип занятия **выделен цветом типа** (Практика — фиолетовый, Лекция — бирюзовый, Лабораторная — янтарный) + `📍 ГК 323` (приглушенный серый).
6. **Превью и юнит-тесты (`ScheduleWidgetPreview.kt`, `ScheduleWidgetUtilsTest.kt`)**:
   - Актуализация превью под новый макет.
   - Обеспечение 100% прохождения тестов и компиляции без ошибок.

---

## 3. Целевые файлы для изменения
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/ScheduleWidget.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/ScheduleWidgetModels.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/ScheduleWidgetUtils.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/ScheduleWidgetTheme.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/ScheduleWidgetPreview.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/components/WidgetHeader.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/components/WidgetHeroCard.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/components/WidgetDayHeader.kt`
- `android/app/src/main/java/ru/lican1907/tusurschedule/widget/components/WidgetLessonCard.kt`
- `android/app/src/test/java/ru/lican1907/tusurschedule/widget/` (тесты утилит)

---

## 4. Критерии приемки
1. Виджет точно повторяет визуальный концепт (шрифты, отступы, цветные лейблы типов, форматирование дат, луна в карточке отдыха).
2. `ScheduleWidgetUtils.kt` остается чистой библиотекой функций без Glance импортов.
3. `ScheduleWidget.kt` остается в рамках лимита (<180 строк).
4. Проект успешно собирается командой `cd android ; .\gradlew :app:compileDebugKotlin ; cd ..`.
