package jsonsupport

final case class Item(name: String, id: Long)

final case class Order(items: List[Item])


