package android.util
import org.xmlpull.v1.XmlPullParser
object Xml { fun newPullParser(): XmlPullParser = org.kxml2.io.KXmlParser() }
