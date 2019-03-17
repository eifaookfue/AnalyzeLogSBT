package jp.co.nri.nefs.tool.models

import java.util.Date

case class Dialog(dialogName: String, handler: Option[String], destinationType: Option[String], userName: String, tradeDate: String, time: Date, startupTime: Long)

//class Dialogs(tag: Tag) extends