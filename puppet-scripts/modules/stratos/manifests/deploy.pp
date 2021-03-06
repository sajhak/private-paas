
#
# Executes the deployment by pushing all necessary configurations and patches

define stratos::deploy ( $security, $target, $owner, $group, $service ) {

  file {
    "/tmp/${service}":
      ensure          => present,
      owner           => $owner,
      group           => $group,
      sourceselect    => all,
      ignore          => '.svn',
      recurse         => true,
      source          => [
	                  "puppet:///modules/stratos/commons/configs/",
                          "puppet:///modules/stratos/${service}/configs/",
                          "puppet:///modules/stratos/${service}/patches/"
			]
  }

  exec {
    "Copy_${name}_modules_to_carbon_home":
      path    => '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/java/bin/',
      command => "cp -r /tmp/${service}/* ${target}/; chown -R ${owner}:${owner} ${target}/; chmod -R 755 ${target}/",
      require => File["/tmp/${service}"];

    "Remove_${name}_temporory_modules_directory":
      path    => '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/java/bin/',
      command => "rm -rf /tmp/${service}",
      require => Exec["Copy_${name}_modules_to_carbon_home"];
  }
}
