package devtools.kafka_data_viewer.ui

import com.fasterxml.jackson.databind.ObjectMapper
import devtools.lib.rxext.ListChangeOps.{AddItems, ListChangeOp, SetList}
import devtools.lib.rxext.LoggableOps.{AppendLogOp, LoggingOp, ResetLogOp}
import devtools.lib.rxext.Observable.just
import devtools.lib.rxext.Subject.{behaviorSubject, publishSubject}
import devtools.lib.rxext.{BehaviorSubject, Observable, Subject}
import devtools.lib.rxui.UiImplicits._
import devtools.lib.rxui._

import scala.language.postfixOps

class PreFilterPane[T](val layoutData: String,
                       allItems: Observable[Seq[T]],
                       currentSelectedItems: Observable[Seq[T]],
                       applySelectedItems: Seq[T] => Unit,
                       filters: BehaviorSubject[Seq[(String, Seq[T])]]
                      ) extends UiComponent {

    private val titleAll = "All"
    private val titleNothing = "Nothing"

    private val filterSelected = publishSubject[String]()
    private val currentName = Subject.behaviorSubject("")
    private val currentNameTrimmed = currentName.map(_.trim())

    private val onSave = publishSubject[Unit]()
    private val onDelete = publishSubject[Unit]()

    private val shownFilters = filters.map(titleAll +: titleNothing +: _.map(_._1))
    private val allowActions = currentNameTrimmed.map(name => !name.isEmpty && !name.equals(titleAll) && !name.equals(titleNothing))

    for (nameAndAllItems <- filterSelected.withLatestFrom(allItems)) applySelectedItems(nameAndAllItems match {
        case (`titleAll`, all) => all
        case (`titleNothing`, _) => Seq()
        case (name, _) => filters.value.find(_._1 == name).get._2
    })

    for (selectionWithName <- onSave.withLatestFrom(Observable.combineLatest(currentSelectedItems, currentNameTrimmed)).map(_._2))
        if (!selectionWithName._2.isEmpty)
            filters << filters.value.toMap.updated(selectionWithName._2, selectionWithName._1).toSeq.sortBy(_._1)

    for (name <- onDelete.withLatestFrom(currentNameTrimmed).map(_._2))
        filters onNext filters.value.filterNot(_._1 == name)

    override def content(): UiWidget = UiPanel(layoutData, Grid(), items = Seq(
        UiLabel("growx", text = just("Pre-filter\n - type name to save\n - select from list to use")),
        UiPanel("growx", Grid("cols 3"), items = Seq(
            UiCombo("growx", text = currentName, selection = filterSelected, items = shownFilters),
            UiButton(text = "Save", onAction = onSave, disabled = allowActions.map(!_)),
            UiButton(text = "Del", onAction = onDelete, disabled = allowActions.map(!_))
        ))
    ))
}

class SearchElementsPane[T](val layoutData: String,
                            elements: Observable[(Seq[T], Boolean)],
                            selection: Subject[T],
                            compare: (T, String) => Boolean,
                            searchText: Subject[String] = publishSubject()
                           ) extends UiComponent {

    private var currentElements: Seq[T] = Seq()
    for (elementsAndReset <- elements) {
        currentElements = elementsAndReset._1
        updateSearchResults()
        if (elementsAndReset._2) {
            currentElement = -1
            if (searchResult.nonEmpty) onNext onNext Unit
        }
    }

    private val searchTextCurrent = behaviorSubject[String]("")
    searchTextCurrent <<< searchText
    private val searchResultsLabel: Subject[String] = Subject.behaviorSubject("Type to search")

    private var searchResult: Seq[T] = Seq()
    private var currentElement = -1

    private val onNext: Subject[Unit] = publishSubject()
    private val onPrev: Subject[Unit] = publishSubject()

    private def updateSearchResults(): Unit = {
        if (searchTextCurrent.value.isEmpty) {
            searchResultsLabel onNext "Nothing is searched"
            searchResult = Seq()
        } else {
            searchResult = currentElements.filter(compare(_, searchTextCurrent.value))
            if (searchResult.nonEmpty) searchResultsLabel onNext ("Result " + (currentElement + 1) + " of " + searchResult.size)
            else searchResultsLabel.onNext("Nothing is found")
        }
    }

    for (searchString <- searchText) {
        currentElement = -1
        updateSearchResults()
        if (searchResult.nonEmpty) onNext onNext Unit
    }

    private def setCurrentSearchElement(): Unit = {
        selection.onNext(searchResult(currentElement))
        searchResultsLabel.onNext("Result " + (currentElement + 1) + " of " + searchResult.size)
    }

    for (_ <- onNext) if (searchResult.nonEmpty) {
        currentElement += 1
        if (currentElement >= searchResult.size) currentElement = 0
        setCurrentSearchElement()
    }
    for (_ <- onPrev) if (searchResult.nonEmpty) {
        currentElement -= 1
        if (currentElement < 0) currentElement = searchResult.size - 1
        setCurrentSearchElement()
    }

    override def content(): UiWidget = UiPanel(layoutData, Grid("cols 5"), items = Seq(
        UiLabel(text = "Search: "),
        UiText("growx", text = searchText),
        UiLabel("w 200", text = searchResultsLabel),
        UiButton(text = "Prev", onAction = onPrev),
        UiButton(text = "Next", onAction = onNext)
    ))
}

class SearchedTextDataPane(val layoutData: String,
                           text: Observable[String],
                           defaultSearch: Observable[String]
                          ) extends UiComponent {
    type SearchElement = (List[Char], Int)
    private val displayText: Subject[String] = publishSubject()
    displayText <<< text

    private val seachItems: Observable[Seq[SearchElement]] = text.map(s =>
        s.toCharArray
                .foldRight(List[List[Char]]())((el, acc) =>
                    acc.headOption.map(head => el :: head).getOrElse(List(el)) :: acc)
                .zipWithIndex)
    private val selection: Subject[SearchElement] = publishSubject()
    private val searchText: Subject[String] = publishSubject()
    searchText <<< defaultSearch
    private val searchTextCurrent = behaviorSubject("")
    searchTextCurrent <<< searchText

    //for (item <- seachItems; first <- item.headOption) selection onNext first

    private def compare(el: SearchElement, s: String) = el._1.startsWith(s.toCharArray)

    private val textSelection: Subject[(Int, Int)] = publishSubject()
    textSelection <<< selection.map(s => (s._2, s._2 + searchTextCurrent.value.length)) //.delay(1000, TimeUnit.MILLISECONDS).observeOn(swtScheduler())

    override def content(): UiWidget =
        UiPanel(layoutData, Grid(), items = Seq(
            new SearchElementsPane[SearchElement]("growx",
                elements = seachItems.map((_, true)),
                selection = selection,
                compare = compare,
                searchText = searchText),
            UiStyledText("grow", multi = true, text = text.asPublishSubject, selection = textSelection)
        ))
}

class TableOutputDataPane[T](val layoutData: String,
                             records: Observable[LoggingOp[T]],
                             compare: (T, String) => Boolean,
                             onLoadNewData: Option[Subject[Unit]] = None,
                             fields: Seq[(String, T => String)],
                             valueField: T => String,
                             sorting: PartialFunction[String, (T, T) => Boolean]
                            ) extends UiComponent {

    private val displayRecords: Subject[ListChangeOp[T]] = publishSubject()
    private val cachedDisplayedRecords = displayRecords.fromListOps()
    private val cachedRecords = scala.collection.mutable.ArrayBuffer[T]()
    private val cachedRecordsSubj: Subject[(Seq[T], Boolean)] = publishSubject()

    private val selectedRecords = publishSubject[Seq[T]]()
    private val searchSelection = publishSubject[T]()
    selectedRecords <<< searchSelection.map(Seq(_))

    private val selectedRecordValue: Subject[String] = publishSubject()
    selectedRecordValue <<< selectedRecords.map(x => x.headOption.map(valueField).getOrElse(""))
    private val selectedRecordJSonValue = selectedRecordValue.map(beautifyJSon)

    for (recordOp <- records) recordOp match {
        case ResetLogOp() =>
            // println("Reset!")
            displayRecords onNext SetList(Nil)
            cachedRecords.clear()
            cachedRecordsSubj onNext(cachedRecords, true)
            selectedRecordValue onNext ""
        case AppendLogOp(items) =>
            // println("Append! " + items.size)
            displayRecords onNext AddItems(items)
            cachedRecords ++= items
            cachedRecordsSubj onNext(cachedRecords, false)
    }

    def sortRecords(key: String, asc: Boolean): Unit = displayRecords onNext
            SetList(cachedRecords.sortWith(if (asc) sorting(key) else !sorting(key)(_, _)))

    private val searchText: Subject[String] = publishSubject()

    def beautifyJSon(text: String): String = try {
        val mapper = new ObjectMapper()
        val json = mapper.readValue(text, classOf[Object])
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
    } catch {case _: Exception => text}

    case class FieldToColumn(title: String, data: T => String, onSort: Option[Subject[Boolean]] = None)

    private val fieldToCols = fields.map(field => FieldToColumn(title = field._1, data = field._2, onSort = if (sorting.isDefinedAt(field._1)) Some(publishSubject()) else None))
    for (f2c <- fieldToCols; onSort <- f2c.onSort; asc <- onSort) sortRecords(f2c.title, asc = asc)

    override def content(): UiWidget = UiPanel(layoutData, Grid(), items = Seq(
        new SearchElementsPane[T]("growx", cachedRecordsSubj, searchSelection, compare, searchText),
        UiSplitPane("grow", orientation = UiVert, proportion = 70, els = (
                UiPanel("", Grid(), items = Seq[Option[UiWidget]](
                    UiTable[T]("grow",
                        items = displayRecords,
                        selection = selectedRecords,
                        columns = fieldToCols.map(f2c => UiColumn[T](title = f2c.title, value = f2c.data, onSort = (asc: Boolean) => f2c.onSort.get << asc))
                    ),
                    onLoadNewData.map(_ => UiLink("growx", text = "<A>Click to read next portion of data</A>", onAction = onLoadNewData)))
                        .filter(_.isDefined).map(_.get)),
                UiTabPanel("grow", tabs = behaviorSubject(Seq(
                    UiTab(label = "Raw Output", content = new SearchedTextDataPane("grow", text = selectedRecordValue, defaultSearch = searchText)),
                    UiTab(label = "JSON Output", content = new SearchedTextDataPane("grow", text = selectedRecordJSonValue, defaultSearch = searchText))
                )))

        ))
    ))
}