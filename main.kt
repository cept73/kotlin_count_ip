/**
 * Определение количества IP адресов в файле
 * ===============================================================
 * v1.1 by Cept
 *
 * В качестве параметра коммандной строки нужно передать имя файла
 * либо будет использоваться из DEFAULT_INPUT_FILENAME
 */
package ip_count

import java.io.File // Требуется работа с файлами
import java.math.BigInteger // Будем вместо хэшей засовывать в целое


// Конфигурация
val DEFAULT_INPUT_FILENAME: String = "ips-list.txt"
var DEBUG: Boolean = true

// Компоненты для хранения
enum class AddressSpace { IPv4, IPv6, Invalid }

data class IPAddressComponents(
    val addressToNode   : Long,
    val addressToPoint  : Int
)

val INVALID = IPAddressComponents(0, 0)


// Результат выполнения
var ipMap = mutableMapOf<Long, BigInteger>()
var ipCount: Int = 0
val powerOf2 = mutableMapOf<Int, BigInteger>()


/**
 * Главная программа
 * 
 * @params аргументы коммандной строки
 */
fun main(args: Array<String>) {
    // Определяемся с входным файлом
    val inputFileName = getFileNameFrom(args)

    // Считаем степени 2-ки
    initPowerOf2()

    // Ждем от пользователя действительное имя файла
    if (!isFileExists( inputFileName )) {
        println("Файл $inputFileName не существует. "
            + "Введите имя исходного файла в коммандной строке.")
        return
    }

    // Парсим файл
    try {
        parseFileLines(inputFileName, ::parserFun)
    }
    catch (e: Exception) {
        println("Фатальная ошибка:")
        println(e)
    }
    finally {
        if (DEBUG) println(ipMap)
        println("\nКоличество адресов: $ipCount\n")
    }
}


/**
 * Основная функция
 */
//@Suppress("UNUSED_VARIABLE")
// addressSpace не используем, иначе будет предупреждение
private fun parserFun(it: Any?): Unit {
    // Разбираем IP из строки
    val ipParsed = ipAddressParse(it as String)

    // Неправильный адрес?
    if (ipParsed == INVALID) return

    // Разложим адрес на ноду, поинта, тип адреса и порт
    val (addressToNode, addressToPoint) = ipParsed

    // Уже был такой адрес? Пропускаем
    val stateOld = getNodeState( addressToNode )
    val stateNew = getStateWithPoint( stateOld, addressToPoint )

    // Если еще не добавляли, состояние поменяется
    if (stateNew != stateOld) {
        // Добавляем в карту хэшей и радуемся
        setNodeState( addressToNode, stateNew )
        ipCount = ipCount + 1

        // Если нужно отладку, выводим IP
        if (DEBUG) {
            println(it)
            // Отключим при количестве больше 10
            if (ipCount > 10) {
                DEBUG = false
                println("Отладка отключена")
            }
        }
    }
}


/* Считаем степени 2-ки от 0 до 255 степени */
fun initPowerOf2()
{
    var value: BigInteger = BigInteger.valueOf(1)
    for (index in 0..255) {
        powerOf2[ index ] = value
        value += value
    }
}


fun getNodeState(addressToNode: Long): BigInteger =
    ipMap[addressToNode] ?: BigInteger.ZERO


fun setNodeState( addressToNode: Long, stateNew: BigInteger )
{
    ipMap[addressToNode] = stateNew
}


fun getStateWithPoint(nodeState: BigInteger, addressToPoint: Int) =
    nodeState.or(powerOf2[addressToPoint])


fun setNodeWithPointState(addressToNode: Long, stateNew: BigInteger?)
{
    ipMap[addressToNode] = stateNew ?: BigInteger.ZERO
}


/**
 * Считываем имя входного файла из коммандной строки
 */
private fun getFileNameFrom(args: Array<String>): String = 
    // Берем первый параметр либо значение по-умолчанию
    when (args.size) {
        0       -> DEFAULT_INPUT_FILENAME   // вызов без параметров
        else    -> args[0]                  // указанный первым
    }


/**
 * Проверка на существование файла
 */
private fun isFileExists(fileName: String) = File(fileName).exists()


/**
 * Просмотр файла, пробегая выполняем parser
 */
private fun parseFileLines(fileName: String, parser: (message: Any?) -> Unit) =
    File(fileName).forEachLine { parser(it) }


/*
 * Готовим парсинг IP
 * 
 * Для каждого IP (например 1.2.3.4)
 * 1) берется первые три числа - называем это нодой. 
 *      (в данном случае 1.2.3)
 * 2) каждое число от 0x00 до 0xFF. переводим в шестнадцатиричный Long
 *      (число 0x010203) 
 * 3) в массиве, где индекс - это адрес ноды из п.2 храним
 *      упакованные поинты в бинарном виде.
 *      сначала там 256 нулей - 32 байта 
 *      (используем BigInteger, TODO: переделать в 4 Long)
 *      Когда встречаем нового поинта, заносим в соответствующий бит.
 *      Если уже установлен бит 1, то пропускаем
 *
 * Итого:
 *      На каждую ноду используем 3 байта на индекс и до 32 байт
 *      на хранение поинтов. Максимальное число нод - 256*256*256
 *      = 16 777 216 (16M), в каждой по 32Б => 
 *      Максимальный размер в памяти 512MB
 */

/**
 * Может быть только IPv4
 */
fun ipAddressParse(ipAddress: String): IPAddressComponents {
    var nodeAddress: Long = 0

    // Разбиваем адрес на куски
    val octets = ipAddress.split('.').reversed().toTypedArray()

    // Вычисляем хэш
    for (i in 1..3) {
        val num = octets[i].toLong()
        nodeAddress = nodeAddress.or(
            num shl ((i - 1) * 8)
            )
    }

    val lastAddressPart = octets[0].toInt()

    // Возвращаем
    // -> (nodeIP, pointIndex, "IPv4", -1 если не указан)
    return IPAddressComponents(
        nodeAddress,
        lastAddressPart
        )
}
