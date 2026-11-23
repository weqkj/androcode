package com.androcode.app.ui

import com.androcode.app.tr
import java.util.Calendar

data class Greeting(val title: String, val subtitle: String)

/** Time-of-day greeting in the spirit of "Coffee and Claude at night?". Picks a variant once per call. */
fun greetingFor(nick: String, variant: Int): Greeting {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val ru: List<Greeting>
    val en: List<Greeting>
    when (h) {
        in 5..10 -> {
            ru = listOf(
                Greeting("Кофе и Androcode с утра?", "Что соберём, пока все ещё спят?"),
                Greeting("Доброе утро, $nick", "Чем займёмся — проектами, папками или сетью?"),
                Greeting("Утро. Кофе уже остыл?", "Расскажи, что настроить на телефоне."),
            )
            en = listOf(
                Greeting("Coffee and Androcode this morning?", "What shall we build while everyone sleeps?"),
                Greeting("Good morning, $nick", "Projects, folders or networking — what first?"),
                Greeting("Morning. Is the coffee cold yet?", "Tell me what to set up on your phone."),
            )
        }
        in 11..16 -> {
            ru = listOf(
                Greeting("Добрый день, $nick", "Какая задача на повестке?"),
                Greeting("Самое время что-нибудь запилить", "Файлы, скрипты, сеть — выбирай."),
                Greeting("Обед и немного рута?", "Скажи, что сделать на устройстве."),
            )
            en = listOf(
                Greeting("Good afternoon, $nick", "What's on the agenda?"),
                Greeting("Perfect time to ship something", "Files, scripts, networking — your pick."),
                Greeting("Lunch and a little root?", "Tell me what to do on the device."),
            )
        }
        in 17..21 -> {
            ru = listOf(
                Greeting("Вечер, $nick. Чай и терминал?", "Что делаем сегодня вечером?"),
                Greeting("Вечерняя смена Androcode", "С чего начнём?"),
                Greeting("Добрый вечер", "Есть идея — расскажи, я запущу."),
            )
            en = listOf(
                Greeting("Evening, $nick. Tea and a terminal?", "What are we doing tonight?"),
                Greeting("The evening shift is on", "Where do we start?"),
                Greeting("Good evening", "Got an idea? Tell me and I'll run it."),
            )
        }
        in 22..23, 0 -> {
            ru = listOf(
                Greeting("Кофе и Androcode ночью?", "Что делаем в раннюю ночь?"),
                Greeting("Ранняя ночь, $nick", "Самое время для хороших идей. С чего начнём?"),
                Greeting("Ночная смена началась", "Чем займёмся, пока тихо?"),
            )
            en = listOf(
                Greeting("Coffee and Androcode at night?", "What are we up to this early night?"),
                Greeting("Early night, $nick", "Prime time for good ideas. Where do we start?"),
                Greeting("The night shift begins", "What shall we do while it's quiet?"),
            )
        }
        else -> {
            ru = listOf(
                Greeting("Не спишь, $nick?", "Глубокая ночь — идеально для рутовых экспериментов."),
                Greeting("Три часа ночи. Классика.", "Что будем чинить или строить?"),
                Greeting("Ночь на дворе, а мы в терминале", "Что на этот раз?"),
            )
            en = listOf(
                Greeting("Still up, $nick?", "Deep night — perfect for root experiments."),
                Greeting("3 a.m. A classic.", "What are we fixing or building?"),
                Greeting("Night outside, terminal inside", "What is it this time?"),
            )
        }
    }
    val list = if (tr("ru", "en") == "ru") ru else en
    return list[variant.mod(list.size)]
}

fun suggestions(): List<String> = listOf(
    tr("Покажи информацию об устройстве и root", "Show device info and root status"),
    tr("Создай папку проекта в /sdcard/Projects", "Create a project folder in /sdcard/Projects"),
    tr("Проверь интернет: IP, DNS, пинг", "Check the internet: IP, DNS, ping"),
    tr("Найди самые тяжёлые приложения", "Find the biggest installed apps"),
)
