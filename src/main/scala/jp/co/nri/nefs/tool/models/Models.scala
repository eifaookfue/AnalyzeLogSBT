package jp.co.nri.nefs.tool.models

import java.util.Date

case class Dialog(handler: String, dialogName: Option[String], action: Option[String],
                  destinationType: Option[String], userName: String, tradeDate: String, time: Date, startupTime: Long)

//class Dialogs(tag: Tag) extends