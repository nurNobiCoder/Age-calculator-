package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.Period
import java.time.Duration
import java.time.temporal.ChronoUnit

data class AgeResult(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalMonths: Long,
    val totalWeeks: Long,
    val totalDays: Long,
    val totalHours: Long,
    val totalMinutes: Long,
    val totalSeconds: Long,
    val remainingDaysInWeek: Int
)

data class CountdownResult(
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalDaysRemaining: Long
)

data class Milestone(
    val title: String,
    val date: LocalDate,
    val percentage: Float,
    val targetDescription: String,
    val isCompleted: Boolean
)

data class ZodiacInfo(
    val westernSign: String,
    val westernSymbol: String,
    val westernTraits: String,
    val chineseSign: String,
    val chineseSymbol: String,
    val chineseTraits: String
)

class AgeCalculatorViewModel : ViewModel() {

    private val _birthDate = MutableStateFlow<LocalDate>(LocalDate.of(1995, 10, 15))
    val birthDate: StateFlow<LocalDate> = _birthDate.asStateFlow()

    private val _birthTime = MutableStateFlow<LocalTime>(LocalTime.of(8, 30))
    val birthTime: StateFlow<LocalTime> = _birthTime.asStateFlow()

    private val _targetDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val targetDate: StateFlow<LocalDate> = _targetDate.asStateFlow()

    private val _isTargetToday = MutableStateFlow<Boolean>(true)
    val isTargetToday: StateFlow<Boolean> = _isTargetToday.asStateFlow()

    // Live Ticking State (updated every second)
    private val _liveSecondsLived = MutableStateFlow<Long>(0)
    val liveSecondsLived: StateFlow<Long> = _liveSecondsLived.asStateFlow()

    // Live Next Birthday Countdown
    private val _liveCountdown = MutableStateFlow<CountdownResult?>(null)
    val liveCountdown: StateFlow<CountdownResult?> = _liveCountdown.asStateFlow()

    init {
        startLiveTicker()
    }

    private fun startLiveTicker() {
        viewModelScope.launch {
            while (true) {
                if (_isTargetToday.value) {
                    val now = LocalDateTime.now()
                    val bDateTime = LocalDateTime.of(_birthDate.value, _birthTime.value)
                    
                    if (now.isAfter(bDateTime)) {
                        val duration = Duration.between(bDateTime, now)
                        _liveSecondsLived.value = duration.seconds
                        _liveCountdown.value = calculateLiveCountdown(now)
                    } else {
                        _liveSecondsLived.value = 0
                        _liveCountdown.value = null
                    }
                } else {
                    // Constant static countdown based on customized Target Date
                    val targetDateTime = LocalDateTime.of(_targetDate.value, LocalTime.MIDNIGHT)
                    val bDateTime = LocalDateTime.of(_birthDate.value, _birthTime.value)
                    if (targetDateTime.isAfter(bDateTime)) {
                        val duration = Duration.between(bDateTime, targetDateTime)
                        _liveSecondsLived.value = duration.seconds
                        _liveCountdown.value = calculateTargetCountdown(_targetDate.value)
                    } else {
                        _liveSecondsLived.value = 0
                        _liveCountdown.value = null
                    }
                }
                delay(1000)
            }
        }
    }

    fun setBirthDate(date: LocalDate) {
        _birthDate.value = date
        recalculate()
    }

    fun setBirthTime(time: LocalTime) {
        _birthTime.value = time
        recalculate()
    }

    fun setTargetDate(date: LocalDate) {
        _targetDate.value = date
        _isTargetToday.value = date.isEqual(LocalDate.now())
        recalculate()
    }

    fun setTodayAsTarget() {
        _targetDate.value = LocalDate.now()
        _isTargetToday.value = true
        recalculate()
    }

    private fun recalculate() {
        // Triggers flow recomposition immediately by changing state
        viewModelScope.launch {
            val now = if (_isTargetToday.value) LocalDateTime.now() else LocalDateTime.of(_targetDate.value, LocalTime.MIDNIGHT)
            val bDateTime = LocalDateTime.of(_birthDate.value, _birthTime.value)
            if (now.isAfter(bDateTime)) {
                _liveSecondsLived.value = Duration.between(bDateTime, now).seconds
                _liveCountdown.value = if (_isTargetToday.value) calculateLiveCountdown(now) else calculateTargetCountdown(_targetDate.value)
            } else {
                _liveSecondsLived.value = 0
                _liveCountdown.value = null
            }
        }
    }

    // Main robust periods calculator
    fun getAgeResult(): AgeResult? {
        val bDate = _birthDate.value
        val tDate = _targetDate.value
        
        if (tDate.isBefore(bDate)) return null

        val period = Period.between(bDate, tDate)
        val totalMonths = ChronoUnit.MONTHS.between(bDate, tDate)
        val totalDays = ChronoUnit.DAYS.between(bDate, tDate)
        val totalWeeks = totalDays / 7
        val remainingDaysInWeek = (totalDays % 7).toInt()
        
        val totalHours = totalDays * 24 + Duration.between(bDate.atStartOfDay(), tDate.atStartOfDay()).toHours() % 24
        val totalMinutes = totalHours * 60
        val totalSeconds = totalMinutes * 60

        return AgeResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalMonths = totalMonths,
            totalWeeks = totalWeeks,
            totalDays = totalDays,
            totalHours = totalHours,
            totalMinutes = totalMinutes,
            totalSeconds = totalSeconds,
            remainingDaysInWeek = remainingDaysInWeek
        )
    }

    private fun calculateLiveCountdown(now: LocalDateTime): CountdownResult {
        val bDate = _birthDate.value
        val nextBirthdayDate = calculateNextBirthday(bDate, now.toLocalDate())
        val birthTimeSelected = _birthTime.value
        val nextBirthdayTime = LocalDateTime.of(nextBirthdayDate, birthTimeSelected)

        val duration = if (now.isBefore(nextBirthdayTime)) {
            Duration.between(now, nextBirthdayTime)
        } else {
            // Already happened today, calculate next year
            Duration.between(now, LocalDateTime.of(calculateNextBirthday(bDate, now.toLocalDate().plusDays(1)), birthTimeSelected))
        }

        val totalDaysRemaining = duration.toDays()
        val hours = (duration.toHours() % 24).toInt()
        val minutes = (duration.toMinutes() % 60).toInt()
        val seconds = (duration.getSeconds() % 60).toInt()

        // Period computation for months and days
        val remainingPeriod = Period.between(now.toLocalDate(), nextBirthdayDate)
        
        return CountdownResult(
            months = remainingPeriod.months,
            days = remainingPeriod.days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalDaysRemaining = totalDaysRemaining
        )
    }

    private fun calculateTargetCountdown(tDate: LocalDate): CountdownResult {
        val bDate = _birthDate.value
        val nextBirthdayDate = calculateNextBirthday(bDate, tDate)
        val duration = Duration.between(tDate.atStartOfDay(), nextBirthdayDate.atStartOfDay())
        
        val totalDaysRemaining = duration.toDays()
        val remainingPeriod = Period.between(tDate, nextBirthdayDate)

        return CountdownResult(
            months = remainingPeriod.months,
            days = remainingPeriod.days,
            hours = 0,
            minutes = 0,
            seconds = 0,
            totalDaysRemaining = totalDaysRemaining
        )
    }

    private fun calculateNextBirthday(birth: LocalDate, reference: LocalDate): LocalDate {
        val nextBirthdayThisYear = birth.withYear(reference.year)
        return if (nextBirthdayThisYear.isBefore(reference)) {
            birth.withYear(reference.year + 1)
        } else {
            nextBirthdayThisYear
        }
    }

    fun getZodiacInfo(): ZodiacInfo {
        val bDate = _birthDate.value
        val wZodiac = getWesternZodiac(bDate)
        val cZodiac = getChineseZodiac(bDate)
        return ZodiacInfo(
            westernSign = wZodiac.first,
            westernSymbol = wZodiac.second,
            westernTraits = getWesternTraits(wZodiac.first),
            chineseSign = cZodiac.first,
            chineseSymbol = cZodiac.second,
            chineseTraits = getChineseTraits(cZodiac.first)
        )
    }

    private fun getWesternZodiac(date: LocalDate): Pair<String, String> {
        val month = date.monthValue
        val day = date.dayOfMonth
        return when (month) {
            1 -> if (day < 20) "Capricorn" to "♑" else "Aquarius" to "♒"
            2 -> if (day < 19) "Aquarius" to "♒" else "Pisces" to "♓"
            3 -> if (day < 21) "Pisces" to "♓" else "Aries" to "♈"
            4 -> if (day < 20) "Aries" to "♈" else "Taurus" to "♉"
            5 -> if (day < 21) "Taurus" to "♉" else "Gemini" to "♊"
            6 -> if (day < 21) "Gemini" to "♊" else "Cancer" to "♋"
            7 -> if (day < 23) "Cancer" to "♋" else "Leo" to "♌"
            8 -> if (day < 23) "Leo" to "♌" else "Virgo" to "♍"
            9 -> if (day < 23) "Virgo" to "♍" else "Libra" to "♎"
            10 -> if (day < 23) "Libra" to "♎" else "Scorpio" to "♏"
            11 -> if (day < 22) "Scorpio" to "♏" else "Sagittarius" to "♐"
            12 -> if (day < 22) "Sagittarius" to "♐" else "Capricorn" to "♑"
            else -> "Unknown" to "✨"
        }
    }

    private fun getWesternTraits(sign: String): String = when (sign) {
        "Aries" -> "Ambitious, direct, brave, and full of vital physical energy."
        "Taurus" -> "Resilient, practical, sensory, loyal, and steady as an anchor."
        "Gemini" -> "Witty, flexible, highly curious, versatile, and sociable."
        "Cancer" -> "Deeply nurturing, intuitive, sentimental, protective, and loving."
        "Leo" -> "Charismatic, proud, generous, creative, and a natural born leader."
        "Virgo" -> "Analytical, precise, helpful, organized, and deeply observant."
        "Libra" -> "Charming, artistic, balanced, diplomatic, and seeks harmony."
        "Scorpio" -> "Intense, passionate, deeply analytical, brave, and mysterious."
        "Sagittarius" -> "Optimistic, philosophical, adventurous, generous, and free-spirited."
        "Capricorn" -> "Disciplined, structured, patient, ambitious, and practical."
        "Aquarius" -> "Idealistic, visionary, unique, original, and independent thinker."
        "Pisces" -> "Dreamy, highly empathetic, creative, compassionate, and wise."
        else -> "Intriguing and full of cosmic wonder."
    }

    private fun getChineseZodiac(date: LocalDate): Pair<String, String> {
        val year = date.year
        val idx = ((year - 4) % 12 + 12) % 12
        val animals = listOf(
            "Rat" to "🐀",
            "Ox" to "🐂",
            "Tiger" to "🐅",
            "Rabbit" to "🐇",
            "Dragon" to "🐉",
            "Snake" to "🐍",
            "Horse" to "🐎",
            "Goat" to "🐑",
            "Monkey" to "🐒",
            "Rooster" to "🐓",
            "Dog" to "🐕",
            "Pig" to "🐖"
        )
        return animals[idx]
    }

    private fun getChineseTraits(animal: String): String = when (animal) {
        "Rat" -> "Quick-witted, resourceful, versatile, and highly observant."
        "Ox" -> "Honest, dependable, industrious, patient, and strong-willed."
        "Tiger" -> "Brave, dynamic, confident, romantic, and ambitious."
        "Rabbit" -> "Gentle, elegant, highly responsible, compassionate, and kind."
        "Dragon" -> "Charismatic, powerful, brave, intelligent, and highly independent."
        "Snake" -> "Wise, mysterious, intuitive, deep, and creative."
        "Horse" -> "Energetic, enthusiastic, independent, warm, and friendly."
        "Goat" -> "Mild-mannered, artistic, gentle, empathetic, and resilient."
        "Monkey" -> "Vibrant, smart, quick, playful, and incredibly adaptive."
        "Rooster" -> "Hardworking, observant, brave, precise, and communicative."
        "Dog" -> "Extremely loyal, honest, compassionate, cautious, and helpful."
        "Pig" -> "Generous, honest, peace-loving, diligent, and noble-hearted."
        else -> "Full of traditional luck and auspicious properties."
    }

    fun getMilestones(): List<Milestone> {
        val bDate = _birthDate.value
        val tDate = _targetDate.value
        val currentDays = ChronoUnit.DAYS.between(bDate, tDate)

        val milestones = mutableListOf<Milestone>()

        // 1. 10,000 Days Milestone
        val date10k = bDate.plusDays(10000)
        val pct10k = (currentDays.toFloat() / 10000f).coerceIn(0f, 1f)
        milestones.add(
            Milestone(
                title = "10,000 Days on Earth",
                date = date10k,
                percentage = pct10k,
                targetDescription = "Live a spectacular 10,000 days of life experience.",
                isCompleted = currentDays >= 10000
            )
        )

        // 2. 20,000 Days Milestone
        val date20k = bDate.plusDays(20000)
        val pct20k = (currentDays.toFloat() / 20000f).coerceIn(0f, 1f)
        milestones.add(
            Milestone(
                title = "20,000 Days on Earth",
                date = date20k,
                percentage = pct20k,
                targetDescription = "A magnificent landmark of over 54 full years of life lived.",
                isCompleted = currentDays >= 20000
            )
        )

        // 3. Quarter-Century (25 Years)
        val date25y = bDate.plusYears(25)
        val currentMonths = ChronoUnit.MONTHS.between(bDate, tDate)
        val pct25y = (currentMonths.toFloat() / (25f * 12f)).coerceIn(0f, 1f)
        milestones.add(
            Milestone(
                title = "Silver Jubilee (25 Years old)",
                date = date25y,
                percentage = pct25y,
                targetDescription = "Entering full maturity and launching adult potentials.",
                isCompleted = ChronoUnit.YEARS.between(bDate, tDate) >= 25
            )
        )

        // 4. Golden Half-Century (50 Years)
        val date50y = bDate.plusYears(50)
        val pct50y = (currentMonths.toFloat() / (50f * 12f)).coerceIn(0f, 1f)
        milestones.add(
            Milestone(
                title = "Golden Jubilee (50 Years old)",
                date = date50y,
                percentage = pct50y,
                targetDescription = "Half a century of accumulation of wisdom and action.",
                isCompleted = ChronoUnit.YEARS.between(bDate, tDate) >= 50
            )
        )

        // 5. Next Decade Milestone
        val curYears = Period.between(bDate, tDate).years
        val nextDecadeAge = ((curYears / 10) + 1) * 10
        val dateDecade = bDate.plusYears(nextDecadeAge.toLong())
        val daysToDecade = ChronoUnit.DAYS.between(bDate, dateDecade)
        val pctDecade = (currentDays.toFloat() / daysToDecade.toFloat()).coerceIn(0f, 1f)
        milestones.add(
            Milestone(
                title = "The Next Decade (Turning $nextDecadeAge)",
                date = dateDecade,
                percentage = pctDecade,
                targetDescription = "Celebrate crossing into the next beautiful phase of life.",
                isCompleted = false
            )
        )

        return milestones.sortedBy { it.date }
    }
}
