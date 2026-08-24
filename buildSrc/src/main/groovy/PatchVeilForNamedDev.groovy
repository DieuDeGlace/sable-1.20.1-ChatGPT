import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Remaps Veil's published SRG bytecode for ModDev's named runtime only. */
abstract class PatchVeilForNamedDev extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getInputJar()

    @InputFile
    abstract RegularFileProperty getMappings()

    @OutputFile
    abstract RegularFileProperty getOutputJar()

    @TaskAction
    void patch() {
        Map<String, String> classes = new HashMap<>()
        Map<String, String> fields = new HashMap<>()
        Map<String, String> methods = new HashMap<>()
        Map<String, Set<String>> fieldFallbacks = new HashMap<>()
        Map<String, Set<String>> methodFallbacks = new HashMap<>()
        Map<String, Set<String>> invokedynamicMethodFallbacks = new HashMap<>()

        mappings.get().asFile.eachLine(StandardCharsets.UTF_8.name()) { line ->
            String[] parts = line.trim().split('\\s+')
            if (parts.length >= 3 && parts[0] == 'CL:') {
                classes.put(parts[1], parts[2])
            } else if (parts.length >= 3 && parts[0] == 'FD:') {
                String oldName = parts[1].substring(parts[1].lastIndexOf('/') + 1)
                String newName = parts[2].substring(parts[2].lastIndexOf('/') + 1)
                fields.put(parts[1], newName)
                Set<String> candidates = fieldFallbacks.get(oldName)
                if (candidates == null) {
                    candidates = new HashSet<>()
                    fieldFallbacks.put(oldName, candidates)
                }
                candidates.add(newName)
            } else if (parts.length >= 5 && parts[0] == 'MD:') {
                String oldName = parts[1].substring(parts[1].lastIndexOf('/') + 1)
                String newName = parts[3].substring(parts[3].lastIndexOf('/') + 1)
                methods.put(parts[1] + '\u0000' + parts[2], newName)
                String fallbackKey = oldName + '\u0000' + parts[2]
                Set<String> candidates = methodFallbacks.get(fallbackKey)
                if (candidates == null) {
                    candidates = new HashSet<>()
                    methodFallbacks.put(fallbackKey, candidates)
                }
                candidates.add(newName)
                Set<String> invokedynamicCandidates = invokedynamicMethodFallbacks.get(oldName)
                if (invokedynamicCandidates == null) {
                    invokedynamicCandidates = new HashSet<>()
                    invokedynamicMethodFallbacks.put(oldName, invokedynamicCandidates)
                }
                invokedynamicCandidates.add(newName)
            }
        }

        assertMapping(fields, 'com/mojang/blaze3d/vertex/PoseStack/f_85834_', 'poseStack')
        assertMapping(methods,
                'com/mojang/blaze3d/vertex/PoseStack/m_252880_\u0000(FFF)V', 'translate')
        assertMapping(methods,
                'net/minecraft/Util/m_137581_\u0000()Lnet/minecraft/Util$OS;', 'getPlatform')
        assertMapping(methods,
                'net/minecraft/server/packs/repository/Pack$ResourcesSupplier/m_247679_\u0000' +
                        '(Ljava/lang/String;)Lnet/minecraft/server/packs/PackResources;', 'open')
        assertMapping(methods,
                'net/minecraft/server/packs/repository/RepositorySource/m_7686_\u0000' +
                        '(Ljava/util/function/Consumer;)V', 'loadPacks')

        int[] mappedFields = [0]
        int[] mappedMethods = [0]
        int[] mappedInvokeDynamicMethods = [0]
        int[] mappedMixinMembers = [0]
        int[] fallbackMembers = [0]
        int[] mixinClasses = [0]
        int[] transformedClasses = [0]

        File source = inputJar.get().asFile
        File target = outputJar.get().asFile
        Files.createDirectories(target.toPath().parent)

        new ZipInputStream(new BufferedInputStream(new FileInputStream(source))).withCloseable { input ->
            new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target))).withCloseable { output ->
                ZipEntry entry
                while ((entry = input.nextEntry) != null) {
                    ZipEntry copy = new ZipEntry(entry.name)
                    copy.time = 0L
                    output.putNextEntry(copy)
                    byte[] data = input.readAllBytes()
                    if (entry.name.endsWith('.class')) {
                        ClassReader reader = new ClassReader(data)
                        String className = reader.className
                        String mixinTarget = findSingleMixinTarget(data)
                        if (mixinTarget != null) {
                            mixinClasses[0]++
                        }

                        Remapper remapper = new Remapper() {
                            @Override
                            String map(String internalName) {
                                return classes.getOrDefault(internalName, internalName)
                            }

                            @Override
                            String mapFieldName(String owner, String name, String descriptor) {
                                String lookupOwner = owner == className && mixinTarget != null
                                        ? mixinTarget : owner
                                String mapped = fields.get(lookupOwner + '/' + name)
                                if (mapped == null && name.startsWith('f_')) {
                                    Set<String> candidates = fieldFallbacks.get(name)
                                    if (candidates != null && candidates.size() == 1) {
                                        mapped = candidates.iterator().next()
                                        fallbackMembers[0]++
                                    }
                                }
                                if (mapped != null) {
                                    mappedFields[0]++
                                    if (lookupOwner != owner) mappedMixinMembers[0]++
                                    return mapped
                                }
                                return name
                            }

                            @Override
                            String mapMethodName(String owner, String name, String descriptor) {
                                String lookupOwner = owner == className && mixinTarget != null
                                        ? mixinTarget : owner
                                String lookupName = name
                                String prefix = ''
                                if (lookupOwner != owner && name.startsWith('shadow$')) {
                                    prefix = 'shadow$'
                                    lookupName = name.substring(prefix.length())
                                }
                                String mapped = methods.get(lookupOwner + '/' + lookupName +
                                        '\u0000' + descriptor)
                                if (mapped == null && lookupName.startsWith('m_')) {
                                    Set<String> candidates = methodFallbacks.get(
                                            lookupName + '\u0000' + descriptor)
                                    if (candidates != null && candidates.size() == 1) {
                                        mapped = candidates.iterator().next()
                                        fallbackMembers[0]++
                                    }
                                }
                                if (mapped != null) {
                                    mappedMethods[0]++
                                    if (lookupOwner != owner) mappedMixinMembers[0]++
                                    return prefix + mapped
                                }
                                return name
                            }

                            @Override
                            String mapInvokeDynamicMethodName(String name, String descriptor) {
                                if (name.startsWith('m_')) {
                                    Set<String> candidates = invokedynamicMethodFallbacks.get(name)
                                    if (candidates != null && candidates.size() == 1) {
                                        mappedInvokeDynamicMethods[0]++
                                        return candidates.iterator().next()
                                    }
                                }
                                return name
                            }
                        }
                        ClassWriter writer = new ClassWriter(0)
                        reader.accept(new ClassRemapper(writer, remapper), 0)
                        data = writer.toByteArray()
                        transformedClasses[0]++
                    }
                    output.write(data)
                    output.closeEntry()
                    input.closeEntry()
                }
            }
        }

        if (transformedClasses[0] == 0 || mixinClasses[0] == 0 ||
                mappedMethods[0] == 0 || mappedInvokeDynamicMethods[0] == 0 ||
                mappedFields[0] == 0 || mappedMixinMembers[0] == 0) {
            target.delete()
            throw new GradleException("Veil 1.0.0.296 named-dev remap validation failed: " +
                    "classes=${transformedClasses[0]}, methods=${mappedMethods[0]}, " +
                    "invokeDynamicMethods=${mappedInvokeDynamicMethods[0]}, " +
                    "fields=${mappedFields[0]}, mixinClasses=${mixinClasses[0]}, " +
                    "mixinMembers=${mappedMixinMembers[0]}, fallbacks=${fallbackMembers[0]}")
        }

        logger.lifecycle("Remapped Veil named-dev jar: {} classes, {} method references, " +
                "{} invokedynamic method names, {} field references, {} mixin members, " +
                "{} unique-name fallbacks",
                transformedClasses[0], mappedMethods[0], mappedInvokeDynamicMethods[0], mappedFields[0],
                mappedMixinMembers[0], fallbackMembers[0])
    }

    static String findSingleMixinTarget(byte[] data) {
        ClassNode node = new ClassNode()
        new ClassReader(data).accept(node,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        List<AnnotationNode> annotations = []
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations)
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations)
        AnnotationNode mixin = annotations.find {
            it.desc == 'Lorg/spongepowered/asm/mixin/Mixin;'
        }
        if (mixin == null || mixin.values == null) return null
        for (int i = 0; i < mixin.values.size(); i += 2) {
            if (mixin.values[i] == 'value') {
                List values = (List) mixin.values[i + 1]
                if (values.size() == 1 && values[0] instanceof Type) {
                    return ((Type) values[0]).internalName
                }
            }
        }
        return null
    }

    private static void assertMapping(Map<String, String> mappings, String key, String expected) {
        String actual = mappings.get(key)
        if (actual != expected) {
            throw new GradleException("Required mapping ${key} expected ${expected}, found ${actual}")
        }
    }
}
