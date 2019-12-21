/**
 * Определение количества IP адресов в файле
 * ===============================================================
 * v1.0 by Cept
 *
 * В качестве параметра коммандной строки нужно передать имя файла
 * либо будет использоваться из DEFAULT_INPUT_FILENAME 
 */
package ip_count

import java.io.File // Требуется работа с файлами
import java.math.BigInteger // Будем вместо хэшей засовывать в целое


// Конфигурация
val DEFAULT_INPUT_FILENAME: String = "ips-list.txt"
val DEBUG: Boolean = true


// Результат выполнения
var ipMap = mutableMapOf<BigInteger, Boolean>()
var ipCount: Int = 0


/**
 * Главная программа
 * 
 * @params аргументы коммандной строки
 */
fun main(args: Array<String>) {
    // Определяемся с входным файлом
    val inputFileName = getFileNameFrom(args)

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
        println("Количество адресов: $ipCount")
    }
}


/**
 * Основная функция
 */
@Suppress("UNUSED_VARIABLE")
// addressSpace не используем, иначе будет предупреждение
private fun parserFun(it: Any?): Unit {
    // Разбираем IP из строки
    val ipParsed = ipAddressParse(it as String)

    // Неправильный адрес?
    if (ipParsed == INVALID) return

    // Разложим на хэш, тип адреса и порт
    val (ourHash, addressSpace, port) = ipParsed

    // Уже был такой адрес? Пропускаем
    if (ourHash in ipMap) return

    // Добавляем в карту хэшей и радуемся
    ipMap[ ourHash ] = true
    ipCount = ipCount + 1

    // Если нужно отладку, выводим IP и хэш
    if (DEBUG) println("$it => ${ourHash.toString(16)}")
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
 * Можно было бы вычислять стандартные хэши строк (как это возможно было
 * сделано ранее через HashSet), но во-первых это не оригинально по условиям
 * задачи, во-вторых программа не распознает, что указан порт в конце например
 * (IP один разные порты => значит для хэша разные IP), в-третьих вдруг быстрее
 * вычисляет
 *
 * Поэтому парсим строку. НО если IPv6 и ошибочных адресов там точно нет, то
 * можно часть проверок поотключать. Хотя мало ли что в файл введут?
 */
enum class AddressSpace { IPv4, IPv6, Invalid }
    
data class IPAddressComponents(
    val address         : BigInteger,
    val addressSpace    : AddressSpace,
    val port            : Int  // -1 значит 'не указан'
)
   
val INVALID = IPAddressComponents(BigInteger.ZERO, AddressSpace.Invalid, 0)

/**
 * Ситуации могут быть разные:
 *   127.0.0.1
 *   127.0.0.1:80
 *   ::1
 *   [::1]:80
 *   2000:2500:0:1::4500:93e3
 *   [2000:2500:0:1::4500:93e3]:80
 *   ::ffff:192.168.1.16
 *   [::ffff:192.168.1.16]:80
 *   1::
 *   ::
 *   256.0.0.0
 *   ::ffff:127.0.0.0.1
 * Или вообще с неправильными адресами:
 *   spam text
 *   (пустая строка)
 *   1000.1.1.1
 */
fun ipAddressParse(ipAddress: String): IPAddressComponents {
    var addressSpace = AddressSpace.IPv4
    var ipa = ipAddress.toLowerCase()
    var port = -1
    var trans = false

    if (ipa == "") return INVALID

    if (ipa.startsWith("::ffff:") && '.' in ipa) {
        addressSpace = AddressSpace.IPv6
        trans = true
        ipa = ipa.drop(7)
    }
    else if (ipa.startsWith("[::ffff:") && '.' in ipa) {
        addressSpace = AddressSpace.IPv6
        trans = true
        ipa = ipa.drop(8).replace("]", "")
    } 
    val octets = ipa.split('.').reversed().toTypedArray()
    var address = BigInteger.ZERO
    if (octets.size == 4) {
        val split = octets[0].split(':')
        if (split.size == 2) {
            val temp = split[1].toIntOrNull()
            if (temp == null || temp !in 0..65535) return INVALID                
            port = temp
            octets[0] = split[0]
        }

        for (i in 0..3) {
            val num = octets[i].toLongOrNull()
            if (num == null || num !in 0..255) return INVALID
            val bigNum = BigInteger.valueOf(num)
            address = address.or(bigNum.shiftLeft(i * 8))
        }

        if (trans) address += BigInteger("ffff00000000", 16)
    }
    else if (octets.size == 1) {
        addressSpace = AddressSpace.IPv6
        if (ipa[0] == '[') {
            ipa = ipa.drop(1)
            val split = ipa.split("]:")
            if (split.size != 2) return INVALID
            val temp = split[1].toIntOrNull()
            if (temp == null || temp !in 0..65535) return INVALID
            port = temp
            ipa = ipa.dropLast(2 + split[1].length)
        }
        val hextets = ipa.split(':').reversed().toMutableList()
        val len = hextets.size
        if (ipa.startsWith("::")) 
            hextets[len - 1] = "0"
        else if (ipa.endsWith("::")) 
            hextets[0] = "0"
        if (ipa == "::") hextets[1] = "0"        
        if (len > 8 || (len == 8 && hextets.any { it == "" })
            || hextets.count { it == "" } > 1)
            return INVALID
        if (len < 8) {
            var insertions = 8 - len
            val lastIndex = kotlin.math.min(hextets.lastIndex, 7)
            for (i in 0..lastIndex) {
                if (hextets[i] == "") {
                    hextets[i] = "0"
                    while (insertions-- > 0) hextets.add(i, "0") 
                    break 
                }
            }
        }
        for (j in 0..7) {
            val num = hextets[j].toLongOrNull(16)
            if (num == null || num !in 0x0..0xFFFF) return INVALID
            val bigNum = BigInteger.valueOf(num)
            address = address.or(bigNum.shiftLeft(j * 16))
        }   
    }
    else return INVALID

    // -> (IP, "IPv4"/"IPv6", -1 если не указан)
    return IPAddressComponents(address, addressSpace, port)
}
