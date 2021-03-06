package com.nexters.ticktock

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.TimePicker
import com.nexters.ticktock.Constant.GPS_ENABLE_REQUEST_CODE
import com.nexters.ticktock.Constant.MAIN_DETAIL_ACTIVITY_REQUEST_CODE
import com.nexters.ticktock.alarmsetting.AlarmSettingSecondActivity
import com.nexters.ticktock.autocomplete.AutoCompleteActivity
import com.nexters.ticktock.dao.TickTockDBHelper
import com.nexters.ticktock.databinding.ActivityMainDetailBinding
import com.nexters.ticktock.model.Alarm
import com.nexters.ticktock.model.enums.Day
import com.nexters.ticktock.model.enums.TickTockColor
import com.nexters.ticktock.timer.AlarmReceiver
import com.nexters.ticktock.utils.*
import java.util.*

class MainDetailActivity: AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainDetailBinding

    private var alarmId: Long = 0
    private var startLocation = ""
    private var endLocation = ""
    private var travelTimeInEdit = Time(0)
    private var hour = 0
    private var minute = 0
    private var startHour = 0
    private var startMinute = 0
    private var dayList: EnumSet<Day> = EnumSet.noneOf(Day::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main_detail)

        getData(intent.getLongExtra("CARD_ID", 0))

        binding.btnClose.setOnClickListener(this)
        binding.cvDeliveryTime.setOnClickListener(this)
        binding.btnEditPrepareTime.setOnClickListener(this)
        binding.layoutSave.setOnClickListener(this)

        binding.tpMeetingTime.setOnTimeChangedListener(object: TimePicker.OnTimeChangedListener{
            override fun onTimeChanged(view: TimePicker?, hourOfDay: Int, minute: Int) {
                startHour = hourOfDay
                startMinute = minute
            }
        })

        binding.edMemo.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) {
                    binding.tvMemoMax.visibility = View.GONE
                    binding.layoutSave.isClickable = true
                    binding.layoutSave.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.btnEnable))
                }
                else if (count == 0) {
                    binding.tvMemoMax.visibility = View.VISIBLE
                    binding.layoutSave.isClickable = false
                    binding.layoutSave.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.btnDisEnable))
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()

        Location.getInstance(this)
        Location.getInstance(this).isGPSConnected()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            // gps 설정 변경 후 재연결
            GPS_ENABLE_REQUEST_CODE -> {
                if (Location.getInstance(this).isGPSConnected())
                    Location.getInstance(this).getLocation()
            }

            MAIN_DETAIL_ACTIVITY_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        hour = data?.getIntExtra("DIRECTION_TIME_HOUR", 0) as Int
                        if (hour != 0) {
                            minute = data?.getIntExtra("DIRECTION_TIME_MINUTE", 0) as Int
                            binding.tvDeliveryTimeSecond.setText("${hour}시간 ${minute}분")
                            travelTimeInEdit = Time(hour * 60 + minute)
                        }
                        else {
                            minute = data.getIntExtra("DIRECTION_TIME_MINUTE", 0)
                            binding.tvDeliveryTimeSecond.setText("${minute}분")
                        }

                        travelTimeInEdit = hour.hour() + minute.minute()
                        startLocation = "${data?.getStringExtra("DIRECTION_START")}"
                        binding.tvDeliveryFromTitle.text = getUnderlinedString("*${startLocation}*")
                        endLocation = "${data?.getStringExtra("DIRECTION_DESTINATION")}"
                        binding.tvDeliveryToTitle.text = getUnderlinedString("*${endLocation}*")
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            binding.btnClose.id -> finish()

            binding.cvDeliveryTime.id -> {
                val intent = Intent(this, AutoCompleteActivity::class.java)
                intent.putExtra("GPS_RESULT", Location.getInstance(this).getResult())
                intent.putExtra("DESTINATION_DATA", binding.tvDeliveryToTitle.text)
                startActivityForResult(intent, MAIN_DETAIL_ACTIVITY_REQUEST_CODE)
            }

            binding.btnEditPrepareTime.id -> {
                val intent = Intent(this, NoneActivity::class.java)
                startActivity(intent)
            }

            binding.layoutSave.id -> {
                setData()

                val alarmDao = TickTockDBHelper.getInstance(this).alarmDao
                val alarmList = alarmDao.findAll()

                // 알람에서 해당 알람 삭제
                for(alarm in alarmList) {
                    if(alarm.id == this.alarmId) {
                        alarmDao.delete(alarm)
                        break
                    }
                }

                //수정된 알람 재 추가
                val calendarSet = Calendar.getInstance()
                val startHour = startHour
                val startMinute = startMinute

                calendarSet.set(Calendar.HOUR_OF_DAY, startHour)
                calendarSet.set(Calendar.MINUTE, startMinute)

                Log.d("StartAlarm", "$startHour:$startMinute")

                val mAlarmIntent:Intent = Intent(this, AlarmReceiver::class.java)
                val pIntent : PendingIntent = PendingIntent.getBroadcast(this, alarmDao.findAll().size, mAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                val alarmManager : AlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val rightNow : Calendar = Calendar.getInstance()

                if(rightNow.timeInMillis < calendarSet.timeInMillis) {

                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendarSet.timeInMillis, pIntent)
                    if(Build.VERSION.SDK_INT >= 23)
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarSet.timeInMillis, pIntent)

                    else if(Build.VERSION.SDK_INT >= 21)
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendarSet.timeInMillis, pIntent)

                    else
                        alarmManager.set(AlarmManager.RTC_WAKEUP, calendarSet.timeInMillis, pIntent)
                }

                finish()
            }
        }
    }

    fun getData(id: Long) {
        alarmId = id
        val alarmDao = TickTockDBHelper.getInstance(this).alarmDao
        alarmDao.findAll().forEach { Log.d("AlarmDao", it.toString()) }
        val alarm = alarmDao.findById(id) as Alarm
        val prepareTime = alarm.steps.map { it.duration }.fold(Time(0)) { acc, time -> acc + time }
        val startTime = alarm.endTime - (alarm.travelTime + prepareTime)

        binding.tvAlarmTime.text = getResizedString("*${startTime.hour}:${startTime.minute}* ${startTime.meridiem}", 3.125f)
        binding.tvDestination.text = alarm.endLocation
        binding.tvDay.text = alarm.days.toString()

        startLocation = alarm.startLocation
        endLocation = alarm.endLocation

        hour = alarm.travelTime.hour
        minute = alarm.travelTime.minute

        travelTimeInEdit = Time(0) + alarm.travelTime

        if (alarm.travelTime.time / 60 != 0) binding.tvDeliveryTimeSecond.text = "${hour}시간 ${minute}분"
        else binding.tvDeliveryTimeSecond.text = "${minute}분"
        binding.tvDeliveryFromTitle.text = getUnderlinedString("*${alarm.startLocation}*")
        binding.tvDeliveryToTitle.text = getUnderlinedString("*${alarm.endLocation}*")

        startHour = startTime.time / 60
        startMinute = startTime.minute
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.tpMeetingTime.hour = startHour
            binding.tpMeetingTime.minute = startMinute
        } else {
            binding.tpMeetingTime.setCurrentHour(startHour)
            binding.tpMeetingTime.setCurrentMinute(startMinute)
        }

        alarm.days.forEach {
            val checkbox = when (it) {
                Day.Monday -> binding.cbMonday
                Day.Tuesday -> binding.cbTuesday
                Day.Wednesday -> binding.cbWednesday
                Day.Thursday -> binding.cbThursday
                Day.Friday -> binding.cbFriday
                Day.Saturday -> binding.cbSaturday
                Day.Sunday -> binding.cbSunday
                else -> binding.cbMonday
            }
            checkbox.isChecked = true
        }

        when (alarm.color) {
            TickTockColor.RED -> {
                binding.layoutTitle.setBackgroundColor(ContextCompat.getColor(this, TickTockColor.RED.cardBgColorId))
                binding.radiogrpColor.check(binding.radiobtn1.id)
            }
            TickTockColor.GREEN -> {
                binding.layoutTitle.setBackgroundColor(ContextCompat.getColor(this, TickTockColor.GREEN.cardBgColorId))
                binding.radiogrpColor.check(binding.radiobtn2.id)
            }
            TickTockColor.YELLOW -> {
                binding.layoutTitle.setBackgroundColor(ContextCompat.getColor(this, TickTockColor.YELLOW.cardBgColorId))
                binding.radiogrpColor.check(binding.radiobtn3.id)
            }
            TickTockColor.BLUE -> {
                binding.layoutTitle.setBackgroundColor(ContextCompat.getColor(this, TickTockColor.BLUE.cardBgColorId))
                binding.radiogrpColor.check(binding.radiobtn4.id)
            }
            TickTockColor.PURPLE -> {
                binding.layoutTitle.setBackgroundColor(ContextCompat.getColor(this, TickTockColor.PURPLE.cardBgColorId))
                binding.radiogrpColor.check(binding.radiobtn5.id)
            }
        }

        if (prepareTime.time / 60 != 0) binding.tvPrepareTimeSecond.text = "${prepareTime.hour}시간 ${prepareTime.minute}분"
        else binding.tvPrepareTimeSecond.text = "${prepareTime.minute}분"

        binding.edMemo.setText(alarm.title)
    }

    fun setData() {

        if (binding.cbMonday.isChecked) dayList.add(Day.Monday)
        if (binding.cbTuesday.isChecked) dayList.add(Day.Tuesday)
        if (binding.cbWednesday.isChecked) dayList.add(Day.Wednesday)
        if (binding.cbThursday.isChecked) dayList.add(Day.Thursday)
        if (binding.cbFriday.isChecked) dayList.add(Day.Friday)
        if (binding.cbSaturday.isChecked) dayList.add(Day.Saturday)
        if (binding.cbSunday.isChecked) dayList.add(Day.Sunday)

        var color = TickTockColor.RED
        when (binding.radiogrpColor.checkedRadioButtonId) {
            binding.radiobtn1.id -> color = TickTockColor.RED
            binding.radiobtn2.id -> color = TickTockColor.GREEN
            binding.radiobtn3.id -> color = TickTockColor.YELLOW
            binding.radiobtn4.id -> color = TickTockColor.BLUE
            binding.radiobtn5.id -> color = TickTockColor.PURPLE
        }

        val alarmDao = TickTockDBHelper.getInstance(this).alarmDao

        alarmDao.save(Alarm(
                id = alarmId,
                days = dayList,
                title = binding.edMemo.text.toString(),
                startLocation = startLocation,
                endLocation = endLocation,
                color = color,
                enable = true,
                endTime = Time(startHour * 60 + startMinute),
                travelTime = travelTimeInEdit
        ))
    }
}
