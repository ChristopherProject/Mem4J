package it.adrian.code.exceptions;

/**
 * Thrown when the given module name cannot be located in the target
 * process's loaded modules / memory mappings.
 */
public class ModuleNotFoundException extends Mem4JException {

    public ModuleNotFoundException(String moduleName) {
        super("Module not found: " + moduleName);
    }
}
