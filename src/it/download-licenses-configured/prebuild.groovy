/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2008 - 2011 CodeLutin, Codehaus, Tony Chemit, Tony chemit
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.nio.file.Path;
import java.nio.file.Files;

final Path basePath = basedir.toPath()

final String baseUri = basePath.toUri().toString()
[
    'src/license/licenses-config-pre-1.18.xml',
    'src/license/licenses-config-since-1.18.xml'
].each {
    final Path configPath = basePath.resolve(it)
    String configContent = new String(Files.readAllBytes(configPath), 'utf-8')
    configContent = configContent.replace('%project.baseUri%', baseUri)
    Files.write(configPath, configContent.getBytes('utf-8'))
}

Files.move(basePath.resolve('target-initial'), basePath.resolve('target'))

final Path asl2 = basePath.resolve('target/no-download/licenses/apache-license-2.0-license-2.0.txt')
assert Files.exists(asl2)
assert asl2.toFile().text.contains('Fake content')

final Path bsd = basePath.resolve('target/no-download/licenses/bsd-3-clause-asm-license.txt')
assert Files.exists(bsd)
assert bsd.toFile().text.contains('Fake content')
