package cromwell.backend.google.pipelines.v2alpha1

import java.time.OffsetDateTime

import com.google.api.services.genomics.model.UnexpectedExitStatusEvent
import com.google.api.services.genomics.v2alpha1.model._
import cromwell.backend.google.pipelines.common.api.PipelinesApiRequestFactory.CreatePipelineParameters
import cromwell.backend.google.pipelines.common.io.PipelinesApiAttachedDisk
import cromwell.backend.google.pipelines.common.{PipelinesApiFileInput, PipelinesApiFileOutput, PipelinesApiLiteralInput, PipelinesApiRuntimeAttributes}
import cromwell.backend.google.pipelines.v2alpha1.api.ActionBuilder._
import cromwell.backend.google.pipelines.v2alpha1.api.ActionFlag
import cromwell.core.ExecutionEvent
import wdl4s.parser.MemoryUnit

import scala.collection.JavaConverters._

object PipelinesConversions {
  implicit class EnhancedEvent(val event: Event) extends AnyVal {
    def toExecutionEvent = ExecutionEvent(event.getDescription, OffsetDateTime.parse(event.getTimestamp))
  }
  
  implicit class DiskConversion(val disk: PipelinesApiAttachedDisk) extends AnyVal {
    def toMount = new Mount()
      .setDisk(disk.name)
      .setPath(disk.mountPoint.pathAsString)

    def toDisk = new Disk()
      .setName(disk.name)
      .setSizeGb(disk.sizeGb)
      .setType(disk.diskType.googleTypeName)
  }

  implicit class EnhancedCreatePipelineParameters(val parameters: CreatePipelineParameters) extends AnyVal {
    def toMounts: List[Mount] = parameters.runtimeAttributes.disks.map(_.toMount).toList
    def toDisks: List[Disk] = parameters.runtimeAttributes.disks.map(_.toDisk).toList
  }

  implicit class EnhancedFileInput(val fileInput: PipelinesApiFileInput) extends AnyVal {
    def toEnvironment = Map(fileInput.name -> fileInput.containerPath)

    def toAction(mounts: List[Mount], projectId: String) = gsutil("-u", projectId, "cp", fileInput.cloudPath, fileInput.containerPath)(mounts, description = Option("localizing"))

    def toMount = {
      new Mount()
        .setDisk(fileInput.mount.name)
        .setPath(fileInput.mount.mountPoint.pathAsString)
    }
  }

  implicit class EnhancedFileOutput(val fileOutput: PipelinesApiFileOutput) extends AnyVal {
    def toEnvironment = Map(fileOutput.name -> fileOutput.containerPath)

    def toAction(mounts: List[Mount], projectId: String, gsutilFlags: List[String] = List.empty) = {
      gsutil("-u", projectId, "cp", fileOutput.containerPath, fileOutput.cloudPath)(mounts, List(ActionFlag.AlwaysRun), description = Option("delocalizing"))
    }

    def toMount = {
      new Mount()
        .setDisk(fileOutput.mount.name)
        .setPath(fileOutput.mount.mountPoint.pathAsString)
    }
  }

  implicit class EnhancedinputLiteral(val literalInput: PipelinesApiLiteralInput) extends AnyVal {
    def toEnvironment = Map(literalInput.name -> literalInput.value)
  }

  implicit class EnhancedAttributes(val attributes: PipelinesApiRuntimeAttributes) extends AnyVal {
    def toMachineType = {
      val cpu = attributes.cpu
      // https://cloud.google.com/genomics/reference/rpc/google.genomics.v2alpha1#virtualmachine
      // https://cloud.google.com/compute/docs/instances/creating-instance-with-custom-machine-type
      val memory = attributes.memory.to(MemoryUnit.MB).asRoundedUpMultipleOf(256).amount.toInt
      s"custom-$cpu-$memory"
    }
  }

  implicit class UnexpectedExitStatusEventDeserialization(val event: UnexpectedExitStatusEvent) extends AnyVal {
    def toErrorMessage(actions: List[Action]): Option[String] = {
      for {
        action <- actions.lift(event.getActionId - 1)
        labels = action.getLabels.asScala.withDefaultValue("N/A")
        description = labels("description")
        command <- labels.get("command")
      } yield s"Action #${event.getActionId} failed. Description: $description. Command: $command Exit Code: ${event.getExitStatus}"
    }
  }
}
