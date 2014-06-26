package mesosphere.marathon.upgrade

import mesosphere.marathon.MarathonSpec
import mesosphere.marathon.api.v1.AppDefinition
import mesosphere.marathon.state.PathId._
import mesosphere.marathon.state.{ ScalingStrategy, Group, Timestamp }
import org.scalatest.{ GivenWhenThen, Matchers }

class DeploymentPlanTest extends MarathonSpec with Matchers with GivenWhenThen {

  /*
  test("start from empty group") {
    val app = AppDefinition("app")
    val from = Group("group", ScalingStrategy(1, None), Set.empty)
    val to = Group("group", ScalingStrategy(1, None), Set(app))
    val plan = DeploymentPlan("plan", from, to)

    plan.toStart should have size 1
    plan.toRestart should have size 0
    plan.toScale should have size 0
    plan.toStop should have size 0
  }

  test("start from running group") {
    val apps = Set(AppDefinition("app", "sleep 10"), AppDefinition("app2", "cmd2"), AppDefinition("app3", "cmd3"))
    val update = Set(AppDefinition("app", "sleep 30"), AppDefinition("app2", "cmd2", instances = 10), AppDefinition("app4", "cmd4"))

    val from = Group("group", ScalingStrategy(1, None), apps)
    val to = Group("group", ScalingStrategy(1, None), update)
    val plan = DeploymentPlan("plan", from, to)

    plan.toStart should have size 1
    plan.toRestart should have size 1
    plan.toScale should have size 1
    plan.toStop should have size 1
  }
  */

  test("when updating a group with dependencies, the correct order is computed") {

    Given("Two application updates with command and scale changes")
    val mongoId = "/test/database/mongo".toPath
    val serviceId = "/test/service/srv1".toPath
    val scaling = ScalingStrategy(0.75, Some(1))
    val mongo = AppDefinition(mongoId, "mng1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) ->
      AppDefinition(mongoId, "mng2", instances = 8, scalingStrategy = scaling)
    val service = AppDefinition(serviceId, "srv1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) ->
      AppDefinition(serviceId, "srv2", dependencies = Set(mongoId), instances = 10, scalingStrategy = scaling)
    val from: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._1)),
      Group("/test/service".toPath, Set(service._1))
    ))

    When("the deployment plan is computed")
    val to: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._2)),
      Group("/test/service".toPath, Set(service._2))
    ))
    val plan = DeploymentPlan(from, to)

    Then("the deployment steps are correct")
    plan.steps should have size 4
    plan.steps(0).actions.toSet should be(Set(RestartApplication(mongo._2, 3, 6)))
    plan.steps(1).actions.toSet should be(Set(RestartApplication(service._2, 3, 8)))
    plan.steps(2).actions.toSet should be(Set(KillAllOldTasksOf(service._2), ScaleApplication(service._2, 10)))
    plan.steps(3).actions.toSet should be(Set(KillAllOldTasksOf(mongo._2), ScaleApplication(mongo._2, 8)))
  }

  test("when updating a group without dependencies, the upgrade can be performed in one step") {
    Given("Two application updates with command and scale changes")
    val mongoId = "/test/database/mongo".toPath
    val serviceId = "/test/service/srv1".toPath
    val scaling = ScalingStrategy(0.75, Some(1))
    val mongo = AppDefinition(mongoId, "mng1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) ->
      AppDefinition(mongoId, "mng2", instances = 8, scalingStrategy = scaling)
    val service = AppDefinition(serviceId, "srv1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) ->
      AppDefinition(serviceId, "srv2", instances = 10, scalingStrategy = scaling)
    val from: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._1)),
      Group("/test/service".toPath, Set(service._1))
    ))

    When("the deployment plan is computed")
    val to: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._2)),
      Group("/test/service".toPath, Set(service._2))
    ))
    val plan = DeploymentPlan(from, to)

    Then("the deployment steps are correct")
    plan.steps should have size 1
    plan.steps(0).actions.toSet should be(Set(RestartApplication(mongo._2, 0, 8), RestartApplication(service._2, 0, 10)))
  }

  test("when updating a group with dependent and independent applications, the correct order is computed") {
    Given("application updates with command and scale changes")
    val mongoId = "/test/database/mongo".toPath
    val serviceId = "/test/service/srv1".toPath
    val appId = "/test/independent/app".toPath
    val scaling = ScalingStrategy(0.75, Some(1))
    val mongo = AppDefinition(mongoId, "mng1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) -> AppDefinition(mongoId, "mng2", instances = 8, scalingStrategy = scaling)
    val service = AppDefinition(serviceId, "srv1", instances = 4, version = Timestamp(0), scalingStrategy = scaling) -> AppDefinition(serviceId, "srv2", dependencies = Set(mongoId), instances = 10, scalingStrategy = scaling)
    val independent = AppDefinition(appId, "app1", instances = 1, version = Timestamp(0), scalingStrategy = scaling) -> AppDefinition(appId, "app2", instances = 3, scalingStrategy = scaling)
    val toStop = AppDefinition("/test/service/toStop".toPath, instances = 1, dependencies = Set(mongoId)) -> None
    val toStart = None -> AppDefinition("/test/service/toStart".toPath, instances = 1, dependencies = Set(serviceId))
    val from: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._1)),
      Group("/test/service".toPath, Set(service._1, toStop._1)),
      Group("/test/independent".toPath, Set(independent._1))
    ))

    When("the deployment plan is computed")
    val to: Group = Group("/test".toPath, groups = Set(
      Group("/test/database".toPath, Set(mongo._2)),
      Group("/test/service".toPath, Set(service._2, toStart._2)),
      Group("/test/independent".toPath, Set(independent._2))
    ))
    val plan = DeploymentPlan(from, to)

    Then("the deployment contains steps for dependent and independent applications")
    plan.steps should have size 6
    plan.steps(0).actions.toSet should be(Set(RestartApplication(mongo._2, 3, 6), RestartApplication(independent._2, 0, 3)))
    plan.steps(1).actions.toSet should be(Set(RestartApplication(service._2, 3, 8)))
    plan.steps(2).actions.toSet should be(Set(StartApplication(toStart._2, 1)))
    plan.steps(3).actions.toSet should be(Set(KillAllOldTasksOf(service._2), ScaleApplication(service._2, 10)))
    plan.steps(4).actions.toSet should be(Set(KillAllOldTasksOf(mongo._2), ScaleApplication(mongo._2, 8)))
    plan.steps(5).actions.toSet should be(Set(StopApplication(toStop._1)))
  }
}