/**
 * Определение количества IP адресов в файле
 * ===============================================================
 * v1.2 by Cept
 *
 * В качестве параметра коммандной строки нужно передать имя файла
 * либо будет использоваться из DEFAULT_INPUT_FILENAME
 */
package ip_count

import java.io.File // Требуется работа с файлами

class App {

    // Конфигурация
    val DEFAULT_INPUT_FILENAME = "ips-list.txt"
    var debugMode = true


    // Информация об адресе
    data class IPAddress(val addressToNode: Long, val addressToPoint: Int)
    val INVALID_IPADDRESS = IPAddress(0, 0)


    // Результат выполнения
    var ipMap = mutableMapOf<Long, LongArray>()
    var ipCount: Int = 0
    val powerOf2 = mutableMapOf<Int, Long>()


    /**
     * Главная программа
     *
     * @params аргументы коммандной строки
     */
    fun run(args: Array<String>) {
        // Определяемся с входным файлом
        val inputFileName = getFileNameFrom(args)

        // Считаем степени 2-ки
        initPowerOf2()

        // Ждем от пользователя действительное имя файла
        if (!isFileExists( inputFileName )) {
            println("File $inputFileName is not exists. "
                    + "Enter filename in command line.")
            return
        }

        // Парсим файл
        try {
            parseFileLines(inputFileName, ::parserFun)
        }
        catch (e: Exception) {
            println("Fatal error:")
            println(e)
        }
        finally {
            if (debugMode) {
                ipMap.forEach {
                    val (nodeAddr, segments) = it

                    print("$nodeAddr -> ")
                    for (item in segments) {
                        print("${item.toString(16)} ")
                    }
                    println()
                }
            }
            println("\nADDRESS COUNT: $ipCount\n")
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
        if (ipParsed == INVALID_IPADDRESS) return

        // Разложим адрес на ноду, поинта, тип адреса и порт
        val (addressToNode, addressToPoint) = ipParsed

        // Уже был такой адрес? Пропускаем
        val stateOld = getSegmentState( addressToNode, addressToPoint )
        val stateNew = getSegmentStateWithPoint( stateOld, addressToPoint )

        // Если еще не добавляли, состояние поменяется
        if (stateNew != stateOld) {
            // Добавляем в карту хэшей и радуемся
            setSegmentState( addressToNode, addressToPoint, stateNew )
            ipCount = ipCount + 1

            // Если нужно отладку, выводим IP
            if (debugMode) {
                println(it)

                // Отключим при количестве больше 10
                if (ipCount > 10) {
                    debugMode = false
                    println("**Debug is turned off**")
                }
            }
        }
    }


    /* Предварительно считаем степени 2-ки для использования */
    fun initPowerOf2()
    {
        var value: Long = 1
        for (index in 0..64) {
            powerOf2[ index ] = value
            value += value
        }
    }


    fun getPointSegment(addressToPoint: Int): Int = addressToPoint / 64


    fun getNode(addressToNode: Long): LongArray = ipMap[addressToNode] ?: longArrayOf(0, 0, 0, 0)


    fun getSegmentState(addressToNode: Long, addressToPoint: Int): Long
    {
        // Находим данные о ноде и адресе
        val node = getNode(addressToNode)
        val segment = getPointSegment(addressToPoint)       

        // Возвращаем сегмент из нее
        return node[segment]
    }


    fun setSegmentState( addressToNode: Long, addressToPoint: Int, stateNew: Long)
    {
        // Находим данные о ноде и адресе
        val segment = getPointSegment(addressToPoint)

        // Is initialized?
        val testerOfExistance = ipMap[addressToNode]?.get(0)
        if (testerOfExistance == null)
            ipMap[addressToNode] = longArrayOf(0, 0, 0, 0)

        // Set value
        ipMap[addressToNode]?.set(segment, stateNew)
    }


    fun getSegmentStateWithPoint(segmentState: Long, addressToPoint: Int): Long 
    {
        val addressBitIndex: Int = addressToPoint % 64
        return segmentState.or( powerOf2[ addressBitIndex ] as Long )
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
     * Готовим парсинг IPv4
     *
     * Для каждого IP (например 1.2.3.4)
     * 1) берется первые три числа - называем это нодой.
     *      (в данном случае 1.2.3)
     * 2) каждое число от 0x00 до 0xFF. переводим в шестнадцатиричный Long
     *      (число 0x010203)
     * 3) в массиве, где индекс - это адрес ноды из п.2 храним
     *      упакованные поинты в бинарном виде.
     *      сначала там 256 нулей - 32 байта
     *      (используем 4 Long)
     *      Когда встречаем нового поинта, заносим в соответствующий бит.
     *      Если уже установлен бит 1, то пропускаем
     *
     * Итого:
     *      На каждую ноду используем 3 байта на индекс и до 32 байт
     *      на хранение поинтов. Максимальное число нод - 256*256*256
     *      = 16 777 216 (16M), в каждой по 32Б =>
     *      Максимальный размер в памяти 512MB
     */
    fun ipAddressParse(ipAddress: String): IPAddress {
        var nodeAddress: Long = 0

        // Разбиваем адрес на куски
        val octets = ipAddress.split('.').reversed().toTypedArray()

        // Вычисляем цифровой адрес ноды
        for (i in 1..3) {
            val num = octets[i].toLong()
            val mask = num shl ((i - 1) * 8)
            nodeAddress = nodeAddress.or(mask)
        }

        // Адрес поинта
        val pointAddress = octets[0].toInt()

        // Возвращаем
        // -> (nodeIP, pointIndex, "IPv4", -1 если не указан)
        return IPAddress(nodeAddress, pointAddress)
    }

}



fun main(args: Array<String>)
{
    App().run(args)
}
