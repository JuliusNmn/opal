/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf
package par

/*
class PKEParallelTasksPropertyStoreTestWithDebugging
    extends PropertyStoreTestWithDebugging(
        List(DefaultPropertyComputation, CheapPropertyComputation)
    ) {

    def createPropertyStore(): PropertyStore = {
        val ps = PKEParallelTasksPropertyStore(new RecordAllPropertyStoreTracer)
        ps.suppressError = true
        ps
    }

}
*/

class PKECPropertyStoreTestWithoutDebugging
    extends PropertyStoreTestWithoutDebugging(
        List(DefaultPropertyComputation, CheapPropertyComputation)
    ) {

    def createPropertyStore(): PropertyStore = {
        val ps = PKECPropertyStore()
        ps.suppressError = true
        ps
    }

}