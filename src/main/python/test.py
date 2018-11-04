from py4j.java_gateway import JavaGateway

gateway = JavaGateway()

app = gateway.entry_point

code = '''

import scala.math.exp

object Example {

  val myFunnyDouble = exp(1.7)


}

'''

res = app.askTypeAt('main', code, 60)

print(res)
