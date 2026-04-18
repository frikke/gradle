/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDependencyIntegrationTest extends AbstractIntegrationSpec {

    def "removing is not supported by task dependencies set"() {
        given:
        buildFile << """
            task t
            task foo { dependsOn t }
            foo.dependsOn.$removalMethod
        """

        when:
        fails()

        then:
        failure.assertHasCause('Removing a task dependency from a task instance is not supported.')

        where:
        removalMethod   | _
        'remove(t)'     | _
        'removeAll([])' | _
        'retainAll([])' | _
    }

    def "can use a closure as a task dependency"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn {
                    bar
                }
            }
        """

        when:
        succeeds("foo")

        then:
        executed(":bar")
    }

    def "accessing task provided to task dependency closure is deprecated"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn { task ->
                    task.toString()
                    bar
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Accessing tasks provided to task dependency closures has been deprecated. This will fail with an error in Gradle 10. Cannot call method 'toString' on task passed to task dependency closure. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#task_in_task_dependency_closure")
        succeeds("foo")
    }

    def "accessing task provided to task dependency closure using it is deprecated"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn {
                    it.toString()
                    bar
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Accessing tasks provided to task dependency closures has been deprecated. This will fail with an error in Gradle 10. Cannot call method 'toString' on task passed to task dependency closure. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#task_in_task_dependency_closure")
        succeeds("foo")
    }

}
