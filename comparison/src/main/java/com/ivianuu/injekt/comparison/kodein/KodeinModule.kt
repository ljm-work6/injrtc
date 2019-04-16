/*
 * Copyright 2018 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.injekt.comparison.kodein

import com.ivianuu.injekt.comparison.*
import org.kodein.di.Kodein
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import org.kodein.di.erased.provider

val kodeinModule = Kodein.Module("fib") {
    bind<Fib1>() with provider { Fib1() }
    bind<Fib2>() with provider { Fib2() }
    bind<Fib3>() with provider { Fib3(instance(), instance()) }
    bind<Fib4>() with provider { Fib4(instance(), instance()) }
    bind<Fib5>() with provider { Fib5(instance(), instance()) }
    bind<Fib6>() with provider { Fib6(instance(), instance()) }
    bind<Fib7>() with provider { Fib7(instance(), instance()) }
    bind<Fib8>() with provider { Fib8(instance(), instance()) }
    bind<Fib9>() with provider { Fib9(instance(), instance()) }
    bind<Fib10>() with provider { Fib10(instance(), instance()) }
    bind<Fib11>() with provider { Fib11(instance(), instance()) }
    bind<Fib12>() with provider { Fib12(instance(), instance()) }
    bind<Fib13>() with provider { Fib13(instance(), instance()) }
    bind<Fib14>() with provider { Fib14(instance(), instance()) }
    bind<Fib15>() with provider { Fib15(instance(), instance()) }
    bind<Fib16>() with provider { Fib16(instance(), instance()) }
    bind<Fib17>() with provider { Fib17(instance(), instance()) }
    bind<Fib18>() with provider { Fib18(instance(), instance()) }
    bind<Fib19>() with provider { Fib19(instance(), instance()) }
    bind<Fib20>() with provider { Fib20(instance(), instance()) }
    bind<Fib21>() with provider { Fib21(instance(), instance()) }
    bind<Fib22>() with provider { Fib22(instance(), instance()) }
    bind<Fib23>() with provider { Fib23(instance(), instance()) }
    bind<Fib24>() with provider { Fib24(instance(), instance()) }
    bind<Fib25>() with provider { Fib25(instance(), instance()) }
    bind<Fib26>() with provider { Fib26(instance(), instance()) }
    bind<Fib27>() with provider { Fib27(instance(), instance()) }
    bind<Fib28>() with provider { Fib28(instance(), instance()) }
    bind<Fib29>() with provider { Fib29(instance(), instance()) }
    bind<Fib30>() with provider { Fib30(instance(), instance()) }
    bind<Fib31>() with provider { Fib31(instance(), instance()) }
    bind<Fib32>() with provider { Fib32(instance(), instance()) }
    bind<Fib33>() with provider { Fib33(instance(), instance()) }
    bind<Fib34>() with provider { Fib34(instance(), instance()) }
    bind<Fib35>() with provider { Fib35(instance(), instance()) }
    bind<Fib36>() with provider { Fib36(instance(), instance()) }
    bind<Fib37>() with provider { Fib37(instance(), instance()) }
    bind<Fib38>() with provider { Fib38(instance(), instance()) }
    bind<Fib39>() with provider { Fib39(instance(), instance()) }
    bind<Fib40>() with provider { Fib40(instance(), instance()) }
    bind<Fib41>() with provider { Fib41(instance(), instance()) }
    bind<Fib42>() with provider { Fib42(instance(), instance()) }
    bind<Fib43>() with provider { Fib43(instance(), instance()) }
    bind<Fib44>() with provider { Fib44(instance(), instance()) }
    bind<Fib45>() with provider { Fib45(instance(), instance()) }
    bind<Fib46>() with provider { Fib46(instance(), instance()) }
    bind<Fib47>() with provider { Fib47(instance(), instance()) }
    bind<Fib48>() with provider { Fib48(instance(), instance()) }
    bind<Fib49>() with provider { Fib49(instance(), instance()) }
    bind<Fib50>() with provider { Fib50(instance(), instance()) }
    bind<Fib51>() with provider { Fib51(instance(), instance()) }
    bind<Fib52>() with provider { Fib52(instance(), instance()) }
    bind<Fib53>() with provider { Fib53(instance(), instance()) }
    bind<Fib54>() with provider { Fib54(instance(), instance()) }
    bind<Fib55>() with provider { Fib55(instance(), instance()) }
    bind<Fib56>() with provider { Fib56(instance(), instance()) }
    bind<Fib57>() with provider { Fib57(instance(), instance()) }
    bind<Fib58>() with provider { Fib58(instance(), instance()) }
    bind<Fib59>() with provider { Fib59(instance(), instance()) }
    bind<Fib60>() with provider { Fib60(instance(), instance()) }
    bind<Fib61>() with provider { Fib61(instance(), instance()) }
    bind<Fib62>() with provider { Fib62(instance(), instance()) }
    bind<Fib63>() with provider { Fib63(instance(), instance()) }
    bind<Fib64>() with provider { Fib64(instance(), instance()) }
    bind<Fib65>() with provider { Fib65(instance(), instance()) }
    bind<Fib66>() with provider { Fib66(instance(), instance()) }
    bind<Fib67>() with provider { Fib67(instance(), instance()) }
    bind<Fib68>() with provider { Fib68(instance(), instance()) }
    bind<Fib69>() with provider { Fib69(instance(), instance()) }
    bind<Fib70>() with provider { Fib70(instance(), instance()) }
    bind<Fib71>() with provider { Fib71(instance(), instance()) }
    bind<Fib72>() with provider { Fib72(instance(), instance()) }
    bind<Fib73>() with provider { Fib73(instance(), instance()) }
    bind<Fib74>() with provider { Fib74(instance(), instance()) }
    bind<Fib75>() with provider { Fib75(instance(), instance()) }
    bind<Fib76>() with provider { Fib76(instance(), instance()) }
    bind<Fib77>() with provider { Fib77(instance(), instance()) }
    bind<Fib78>() with provider { Fib78(instance(), instance()) }
    bind<Fib79>() with provider { Fib79(instance(), instance()) }
    bind<Fib80>() with provider { Fib80(instance(), instance()) }
    bind<Fib81>() with provider { Fib81(instance(), instance()) }
    bind<Fib82>() with provider { Fib82(instance(), instance()) }
    bind<Fib83>() with provider { Fib83(instance(), instance()) }
    bind<Fib84>() with provider { Fib84(instance(), instance()) }
    bind<Fib85>() with provider { Fib85(instance(), instance()) }
    bind<Fib86>() with provider { Fib86(instance(), instance()) }
    bind<Fib87>() with provider { Fib87(instance(), instance()) }
    bind<Fib88>() with provider { Fib88(instance(), instance()) }
    bind<Fib89>() with provider { Fib89(instance(), instance()) }
    bind<Fib90>() with provider { Fib90(instance(), instance()) }
    bind<Fib91>() with provider { Fib91(instance(), instance()) }
    bind<Fib92>() with provider { Fib92(instance(), instance()) }
    bind<Fib93>() with provider { Fib93(instance(), instance()) }
    bind<Fib94>() with provider { Fib94(instance(), instance()) }
    bind<Fib95>() with provider { Fib95(instance(), instance()) }
    bind<Fib96>() with provider { Fib96(instance(), instance()) }
    bind<Fib97>() with provider { Fib97(instance(), instance()) }
    bind<Fib98>() with provider { Fib98(instance(), instance()) }
    bind<Fib99>() with provider { Fib99(instance(), instance()) }
    bind<Fib100>() with provider { Fib100(instance(), instance()) }
}
